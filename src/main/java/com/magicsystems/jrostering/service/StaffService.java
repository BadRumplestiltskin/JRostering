package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Service responsible for managing core {@link Staff} data: CRUD, availability
 * windows, scheduling preferences, and leave.
 *
 * <p>Qualification, site-assignment, and relationship (incompatibility/pairing)
 * operations have been extracted to {@link StaffQualificationService},
 * {@link StaffAssignmentService}, and {@link StaffRelationshipService} respectively
 * to keep constructor injection size manageable.</p>
 *
 * <h3>Lifecycle</h3>
 * <p>Staff are never hard-deleted. Deactivation ({@link #deactivate}) sets
 * {@code active = false}. Existing shift assignments and leave records are
 * intentionally preserved; the roster manager is responsible for handling
 * the downstream effects on future roster periods.</p>
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class StaffService {

    // =========================================================================
    // Request records
    // =========================================================================

    /**
     * Input for creating a new staff member.
     *
     * @param firstName               required
     * @param lastName                required
     * @param email                   required; must be unique within the organisation
     * @param phone                   optional
     * @param employmentType          required
     * @param contractedHoursPerWeek  optional; null for casual staff
     * @param hourlyRate              optional
     */
    public record StaffCreateRequest(
            String firstName,
            String lastName,
            String email,
            String phone,
            EmploymentType employmentType,
            BigDecimal contractedHoursPerWeek,
            BigDecimal hourlyRate
    ) {}

    /**
     * Input for updating an existing staff member.
     * All fields are replaced; supply the current value for fields that are not changing.
     */
    public record StaffUpdateRequest(
            String firstName,
            String lastName,
            String email,
            String phone,
            EmploymentType employmentType,
            BigDecimal contractedHoursPerWeek,
            BigDecimal hourlyRate
    ) {}

    // =========================================================================
    // Dependencies
    // =========================================================================

    private final OrganisationRepository      organisationRepository;
    private final StaffRepository             staffRepository;
    private final ShiftTypeRepository         shiftTypeRepository;
    private final StaffAvailabilityRepository staffAvailabilityRepository;
    private final StaffPreferenceRepository   staffPreferenceRepository;
    private final LeaveRepository             leaveRepository;

    public StaffService(
            OrganisationRepository organisationRepository,
            StaffRepository staffRepository,
            ShiftTypeRepository shiftTypeRepository,
            StaffAvailabilityRepository staffAvailabilityRepository,
            StaffPreferenceRepository staffPreferenceRepository,
            LeaveRepository leaveRepository
    ) {
        this.organisationRepository    = organisationRepository;
        this.staffRepository           = staffRepository;
        this.shiftTypeRepository       = shiftTypeRepository;
        this.staffAvailabilityRepository = staffAvailabilityRepository;
        this.staffPreferenceRepository = staffPreferenceRepository;
        this.leaveRepository           = leaveRepository;
    }

    // =========================================================================
    // Staff CRUD
    // =========================================================================

    /**
     * Returns a staff member by ID.
     *
     * @throws EntityNotFoundException if no staff member exists with the given ID
     */
    public Staff getById(Long staffId) {
        return staffRepository.findById(staffId)
                .orElseThrow(() -> EntityNotFoundException.of("Staff", staffId));
    }

    /**
     * Returns all active staff members belonging to the given organisation.
     *
     * @throws EntityNotFoundException if the organisation does not exist
     */
    public List<Staff> getAllActiveByOrganisation(Long organisationId) {
        Organisation organisation = requireOrganisation(organisationId);
        return staffRepository.findByOrganisationAndActiveTrue(organisation);
    }

    /**
     * Creates a new staff member within the given organisation.
     * Email must be unique within the organisation.
     *
     * @throws EntityNotFoundException   if the organisation does not exist
     * @throws InvalidOperationException if a staff member with the same email
     *                                   already exists in this organisation
     */
    @Transactional
    public Staff create(Long organisationId, StaffCreateRequest request) {
        Organisation organisation = requireOrganisation(organisationId);

        if (staffRepository.findByOrganisationAndActiveTrue(organisation).stream()
                .anyMatch(s -> s.getEmail().equalsIgnoreCase(request.email()))) {
            throw new InvalidOperationException(
                    "A staff member with email '" + request.email() + "' already exists in this organisation.");
        }

        Staff staff = new Staff();
        staff.setOrganisation(organisation);
        applyStaffFields(staff, request.firstName(), request.lastName(), request.email(),
                request.phone(), request.employmentType(), request.contractedHoursPerWeek(), request.hourlyRate());

        Staff saved = staffRepository.save(staff);
        log.info("Created staff id={} email={} in organisation id={}", saved.getId(), saved.getEmail(), organisationId);
        return saved;
    }

    /**
     * Updates the core fields of an existing staff member.
     * The staff member's organisation cannot be changed.
     *
     * @throws EntityNotFoundException   if the staff member does not exist
     * @throws InvalidOperationException if the updated email conflicts with another
     *                                   active staff member in the same organisation
     */
    @Transactional
    public Staff update(Long staffId, StaffUpdateRequest request) {
        Staff staff = requireStaff(staffId);

        boolean emailChanged = !staff.getEmail().equalsIgnoreCase(request.email());
        if (emailChanged) {
            boolean emailTaken = staffRepository
                    .findByOrganisationAndActiveTrue(staff.getOrganisation()).stream()
                    .anyMatch(s -> !s.getId().equals(staffId)
                            && s.getEmail().equalsIgnoreCase(request.email()));
            if (emailTaken) {
                throw new InvalidOperationException(
                        "A staff member with email '" + request.email() + "' already exists in this organisation.");
            }
        }

        applyStaffFields(staff, request.firstName(), request.lastName(), request.email(),
                request.phone(), request.employmentType(), request.contractedHoursPerWeek(), request.hourlyRate());

        return staffRepository.save(staff);
    }

    /**
     * Deactivates a staff member. Existing shift assignments and leave records
     * are preserved; the roster manager is responsible for handling downstream
     * effects on future roster periods.
     *
     * @throws EntityNotFoundException if the staff member does not exist
     */
    @Transactional
    public Staff deactivate(Long staffId) {
        Staff staff = requireStaff(staffId);
        staff.setActive(false);
        log.info("Deactivated staff id={}", staffId);
        return staffRepository.save(staff);
    }

    // =========================================================================
    // Availability management
    // =========================================================================

    /**
     * Returns all availability windows declared by a staff member.
     *
     * @throws EntityNotFoundException if the staff member does not exist
     */
    public List<StaffAvailability> getAvailability(Long staffId) {
        return staffAvailabilityRepository.findByStaff(requireStaff(staffId));
    }

    /**
     * Adds a recurring weekly availability window for a staff member.
     *
     * <p>Overnight windows crossing midnight (e.g. 22:00–06:00) are not supported.
     * {@code endTime} must be strictly after {@code startTime}.</p>
     *
     * @throws EntityNotFoundException   if the staff member does not exist
     * @throws InvalidOperationException if {@code endTime} is not after {@code startTime}
     */
    @Transactional
    public StaffAvailability addAvailability(Long staffId, DayOfWeek dayOfWeek,
                                              LocalTime startTime,
                                              LocalTime endTime,
                                              boolean available) {
        Staff staff = requireStaff(staffId);

        if (!endTime.isAfter(startTime)) {
            throw new InvalidOperationException(
                    "Availability end time " + endTime + " must be strictly after start time "
                    + startTime + ". Overnight windows crossing midnight are not supported.");
        }

        StaffAvailability window = new StaffAvailability();
        window.setStaff(staff);
        window.setDayOfWeek(dayOfWeek);
        window.setStartTime(startTime);
        window.setEndTime(endTime);
        window.setAvailable(available);
        return staffAvailabilityRepository.save(window);
    }

    /**
     * Removes an availability window.
     *
     * @throws EntityNotFoundException if the availability record does not exist
     */
    @Transactional
    public void removeAvailability(Long availabilityId) {
        StaffAvailability window = staffAvailabilityRepository.findById(availabilityId)
                .orElseThrow(() -> EntityNotFoundException.of("StaffAvailability", availabilityId));
        staffAvailabilityRepository.delete(window);
    }

    // =========================================================================
    // Preference management
    // =========================================================================

    /**
     * Returns all scheduling preferences declared by a staff member.
     *
     * @throws EntityNotFoundException if the staff member does not exist
     */
    public List<StaffPreference> getPreferences(Long staffId) {
        return staffPreferenceRepository.findByStaff(requireStaff(staffId));
    }

    /**
     * Adds a scheduling preference for a staff member.
     *
     * <p>For {@link PreferenceType#PREFERRED_DAY_OFF}: supply {@code dayOfWeek};
     * {@code shiftTypeId} must be {@code null}.</p>
     * <p>For {@link PreferenceType#PREFERRED_SHIFT_TYPE} or
     * {@link PreferenceType#AVOID_SHIFT_TYPE}: supply {@code shiftTypeId};
     * {@code dayOfWeek} must be {@code null}.</p>
     *
     * @throws EntityNotFoundException   if the staff member or shift type does not exist
     * @throws InvalidOperationException if the dayOfWeek / shiftTypeId combination
     *                                   is inconsistent with the preference type, or if an
     *                                   identical preference already exists for this staff member
     */
    @Transactional
    public StaffPreference addPreference(Long staffId, PreferenceType preferenceType,
                                          DayOfWeek dayOfWeek, Long shiftTypeId) {
        Staff staff = requireStaff(staffId);
        validatePreferenceFields(preferenceType, dayOfWeek, shiftTypeId);

        StaffPreference preference = new StaffPreference();
        preference.setStaff(staff);
        preference.setPreferenceType(preferenceType);
        preference.setDayOfWeek(dayOfWeek);

        if (shiftTypeId != null) {
            ShiftType shiftType = shiftTypeRepository.findById(shiftTypeId)
                    .orElseThrow(() -> EntityNotFoundException.of("ShiftType", shiftTypeId));

            if (staffPreferenceRepository.existsByStaffAndPreferenceTypeAndShiftType(
                    staff, preferenceType, shiftType)) {
                throw new InvalidOperationException(
                        "Staff id=" + staffId + " already has a " + preferenceType
                        + " preference for shift type id=" + shiftTypeId);
            }
            preference.setShiftType(shiftType);
        } else {
            if (staffPreferenceRepository.existsByStaffAndPreferenceTypeAndDayOfWeek(
                    staff, preferenceType, dayOfWeek)) {
                throw new InvalidOperationException(
                        "Staff id=" + staffId + " already has a " + preferenceType
                        + " preference for " + dayOfWeek);
            }
        }

        return staffPreferenceRepository.save(preference);
    }

    /**
     * Removes a scheduling preference.
     *
     * @throws EntityNotFoundException if the preference does not exist
     */
    @Transactional
    public void removePreference(Long preferenceId) {
        StaffPreference preference = staffPreferenceRepository.findById(preferenceId)
                .orElseThrow(() -> EntityNotFoundException.of("StaffPreference", preferenceId));
        staffPreferenceRepository.delete(preference);
    }

    // =========================================================================
    // Leave management
    // =========================================================================

    /**
     * Returns all leave records for a staff member.
     *
     * @throws EntityNotFoundException if the staff member does not exist
     */
    public List<Leave> getLeave(Long staffId) {
        return leaveRepository.findByStaffOrderByStartDateAsc(requireStaff(staffId));
    }

    /**
     * Adds a leave record for a staff member.
     * New leave is always created with status {@link LeaveStatus#REQUESTED}.
     * Use {@link #updateLeaveStatus} to approve or reject it.
     *
     * @throws EntityNotFoundException   if the staff member does not exist
     * @throws InvalidOperationException if {@code endDate} is before {@code startDate}, or if
     *                                   an existing REQUESTED or APPROVED leave already overlaps
     *                                   the requested date range for this staff member
     */
    @Transactional
    public Leave addLeave(Long staffId, LocalDate startDate, LocalDate endDate,
                           LeaveType leaveType, String notes) {
        Staff staff = requireStaff(staffId);

        if (endDate.isBefore(startDate)) {
            throw new InvalidOperationException(
                    "Leave end date " + endDate + " cannot be before start date " + startDate);
        }

        var overlapping = leaveRepository.findOverlapping(
                staff,
                EnumSet.of(LeaveStatus.REQUESTED, LeaveStatus.APPROVED),
                startDate, endDate, null);
        if (!overlapping.isEmpty()) {
            Leave existing = overlapping.getFirst();
            throw new InvalidOperationException(
                    "Staff id=" + staffId + " already has " + existing.getStatus()
                    + " leave from " + existing.getStartDate() + " to " + existing.getEndDate()
                    + " that overlaps the requested period.");
        }

        Leave leave = new Leave();
        leave.setStaff(staff);
        leave.setStartDate(startDate);
        leave.setEndDate(endDate);
        leave.setLeaveType(leaveType);
        leave.setStatus(LeaveStatus.REQUESTED);
        leave.setNotes(notes);
        return leaveRepository.save(leave);
    }

    /**
     * Updates the approval status of a leave record.
     * Only valid transitions: REQUESTED → APPROVED, REQUESTED → REJECTED.
     *
     * <p>When approving, checks that no other APPROVED leave already overlaps
     * this date range for the same staff member.</p>
     *
     * @throws EntityNotFoundException   if the leave record does not exist
     * @throws InvalidOperationException if the transition is not permitted, or if
     *                                   approving would create an overlap with existing
     *                                   APPROVED leave for the same staff member
     */
    @Transactional
    public Leave updateLeaveStatus(Long leaveId, LeaveStatus newStatus) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> EntityNotFoundException.of("Leave", leaveId));

        if (leave.getStatus() != LeaveStatus.REQUESTED) {
            throw new InvalidOperationException(
                    "Leave id=" + leaveId + " is already " + leave.getStatus()
                    + ". Only REQUESTED leave may have its status changed.");
        }
        if (newStatus == LeaveStatus.REQUESTED) {
            throw new InvalidOperationException("Cannot transition leave status to REQUESTED.");
        }

        if (newStatus == LeaveStatus.APPROVED) {
            var overlapping = leaveRepository.findOverlapping(
                    leave.getStaff(),
                    EnumSet.of(LeaveStatus.APPROVED),
                    leave.getStartDate(), leave.getEndDate(), leaveId);
            if (!overlapping.isEmpty()) {
                Leave existing = overlapping.getFirst();
                throw new InvalidOperationException(
                        "Cannot approve leave id=" + leaveId + ": staff id="
                        + leave.getStaff().getId() + " already has APPROVED leave from "
                        + existing.getStartDate() + " to " + existing.getEndDate()
                        + " that overlaps this period.");
            }
        }

        leave.setStatus(newStatus);
        return leaveRepository.save(leave);
    }

    /**
     * Removes a leave record. Only REQUESTED leave may be removed.
     * Approved leave cannot be deleted to preserve the audit trail.
     *
     * @throws EntityNotFoundException   if the leave record does not exist
     * @throws InvalidOperationException if the leave is not in REQUESTED status
     */
    @Transactional
    public void removeLeave(Long leaveId) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> EntityNotFoundException.of("Leave", leaveId));

        if (leave.getStatus() != LeaveStatus.REQUESTED) {
            throw new InvalidOperationException(
                    "Only REQUESTED leave may be removed. Leave id=" + leaveId
                    + " has status " + leave.getStatus());
        }

        leaveRepository.delete(leave);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Organisation requireOrganisation(Long id) {
        return organisationRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Organisation", id));
    }

    private Staff requireStaff(Long id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Staff", id));
    }

    private void applyStaffFields(Staff staff, String firstName, String lastName,
                                   String email, String phone, EmploymentType employmentType,
                                   BigDecimal contractedHoursPerWeek, BigDecimal hourlyRate) {
        staff.setFirstName(firstName);
        staff.setLastName(lastName);
        staff.setEmail(email);
        staff.setPhone(phone);
        staff.setEmploymentType(employmentType);
        staff.setContractedHoursPerWeek(contractedHoursPerWeek);
        staff.setHourlyRate(hourlyRate);
    }

    private void validatePreferenceFields(PreferenceType type, DayOfWeek dayOfWeek, Long shiftTypeId) {
        if (type == PreferenceType.PREFERRED_DAY_OFF) {
            if (dayOfWeek == null) {
                throw new InvalidOperationException("PREFERRED_DAY_OFF preference requires dayOfWeek.");
            }
            if (shiftTypeId != null) {
                throw new InvalidOperationException("PREFERRED_DAY_OFF preference must not have a shiftTypeId.");
            }
        } else {
            if (shiftTypeId == null) {
                throw new InvalidOperationException(type + " preference requires shiftTypeId.");
            }
            if (dayOfWeek != null) {
                throw new InvalidOperationException(type + " preference must not have a dayOfWeek.");
            }
        }
    }

}
