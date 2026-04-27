package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for managing the roster planning lifecycle:
 * {@link RosterPeriod} creation and state transitions, {@link Shift} management
 * with automatic slot reconciliation, and manual {@link ShiftAssignment} operations.
 *
 * <h3>Roster period state machine</h3>
 * <pre>
 * DRAFT → SOLVING  (managed by SolverService)
 *       → CANCELLED
 *
 * SOLVING → SOLVED     (managed by SolverService on completion)
 *         → INFEASIBLE (managed by SolverService on infeasible result)
 *         → CANCELLED  (managed by SolverService on cancellation)
 *
 * SOLVED    → DRAFT     (revertToDraft — cascades to period 2 if present)
 *           → PUBLISHED
 *           → CANCELLED
 *
 * PUBLISHED → DRAFT     (revertToDraft — cascades to period 2 if present)
 *           → CANCELLED
 *
 * INFEASIBLE → DRAFT    (revertToDraft)
 *            → CANCELLED
 * </pre>
 *
 * <h3>Shift modifications on non-DRAFT periods</h3>
 * <p>Shifts may be added, edited, or removed on {@code SOLVED}, {@code PUBLISHED},
 * and {@code INFEASIBLE} periods. Any such modification automatically reverts the
 * period to {@code DRAFT} (and cascades to period 2 if applicable), since the
 * existing solve result is no longer valid.</p>
 *
 * <h3>Slot auto-creation and reconciliation</h3>
 * <p>When a {@link Shift} is saved, {@link ShiftAssignment} slots are automatically
 * managed to match {@code minimumStaff}. When the count is reduced, only unassigned
 * and non-pinned slots are removed; occupied slots are never removed automatically.</p>
 *
 * <h3>Sequential periods</h3>
 * <p>A second roster period (sequence 2) may only be created once period 1 is
 * {@code SOLVED} or {@code PUBLISHED}. Period 2 must already exist before period 1
 * can be replanned — it will have its assignments cleared and revert to {@code DRAFT}
 * automatically when period 1 is reverted.</p>
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class RosterService {

    // =========================================================================
    // Request records
    // =========================================================================

    /**
     * Input for creating a new shift within a roster period.
     *
     * @param shiftTypeId   optional; enables PREFERRED_SHIFT_TYPE constraint evaluation
     * @param name          optional display label
     * @param startDatetime required
     * @param endDatetime   required; must be after startDatetime
     * @param minimumStaff  minimum staff slots to create; must be at least 1
     * @param notes         optional
     */
    public record ShiftCreateRequest(
            Long shiftTypeId,
            String name,
            OffsetDateTime startDatetime,
            OffsetDateTime endDatetime,
            int minimumStaff,
            String notes
    ) {}

    /**
     * Input for updating an existing shift.
     * All fields are replaced; supply the current value for fields that are not changing.
     */
    public record ShiftUpdateRequest(
            Long shiftTypeId,
            String name,
            OffsetDateTime startDatetime,
            OffsetDateTime endDatetime,
            int minimumStaff,
            String notes
    ) {}

    // =========================================================================
    // Constants
    // =========================================================================

    /** Period statuses from which a period may be reverted to DRAFT. */
    private static final Set<RosterPeriodStatus> REVERTABLE_STATUSES = Set.of(
            RosterPeriodStatus.SOLVED,
            RosterPeriodStatus.PUBLISHED,
            RosterPeriodStatus.INFEASIBLE
    );

    /** Period statuses that permit shift modifications (modifications trigger a revert to DRAFT). */
    private static final Set<RosterPeriodStatus> MODIFIABLE_STATUSES = Set.of(
            RosterPeriodStatus.DRAFT,
            RosterPeriodStatus.SOLVED,
            RosterPeriodStatus.PUBLISHED,
            RosterPeriodStatus.INFEASIBLE
    );

    // =========================================================================
    // Dependencies
    // =========================================================================

    private final SiteRepository                       siteRepository;
    private final RosterPeriodRepository               rosterPeriodRepository;
    private final ShiftRepository                      shiftRepository;
    private final ShiftAssignmentRepository            shiftAssignmentRepository;
    private final ShiftQualificationRequirementRepository shiftQualRequirementRepository;
    private final QualificationRepository              qualificationRepository;
    private final ShiftTypeRepository                  shiftTypeRepository;
    private final StaffRepository                      staffRepository;

    public RosterService(SiteRepository siteRepository,
                         RosterPeriodRepository rosterPeriodRepository,
                         ShiftRepository shiftRepository,
                         ShiftAssignmentRepository shiftAssignmentRepository,
                         ShiftQualificationRequirementRepository shiftQualRequirementRepository,
                         QualificationRepository qualificationRepository,
                         ShiftTypeRepository shiftTypeRepository,
                         StaffRepository staffRepository) {
        this.siteRepository               = siteRepository;
        this.rosterPeriodRepository       = rosterPeriodRepository;
        this.shiftRepository              = shiftRepository;
        this.shiftAssignmentRepository    = shiftAssignmentRepository;
        this.shiftQualRequirementRepository = shiftQualRequirementRepository;
        this.qualificationRepository      = qualificationRepository;
        this.shiftTypeRepository          = shiftTypeRepository;
        this.staffRepository              = staffRepository;
    }

    // =========================================================================
    // RosterPeriod queries
    // =========================================================================

    /**
     * Returns a roster period by ID.
     *
     * @throws EntityNotFoundException if no roster period exists with the given ID
     */
    public RosterPeriod getById(Long rosterPeriodId) {
        return requireRosterPeriod(rosterPeriodId);
    }

    /**
     * Returns all roster periods for a site, ordered chronologically.
     *
     * @throws EntityNotFoundException if the site does not exist
     */
    public List<RosterPeriod> getBySite(Long siteId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> EntityNotFoundException.of("Site", siteId));
        return rosterPeriodRepository.findBySiteOrderByStartDateAsc(site);
    }

    // =========================================================================
    // RosterPeriod lifecycle
    // =========================================================================

    /**
     * Creates a new roster period for a site.
     *
     * <p>If {@code previousPeriodId} is null, the period is treated as the first in a
     * new sequence (sequenceNumber = 1). If provided, it is treated as period 2
     * (sequenceNumber = 2) and the following rules apply:</p>
     * <ul>
     *   <li>The referenced previous period must be in {@code SOLVED} or {@code PUBLISHED} status.</li>
     *   <li>{@code startDate} must be exactly one day after the previous period's end date.</li>
     * </ul>
     * <p>The end date is always set to {@code startDate + 13 days}.</p>
     *
     * @throws EntityNotFoundException   if the site or previous period does not exist
     * @throws InvalidOperationException if the previous period is not SOLVED/PUBLISHED,
     *                                   or if the start date does not follow the previous period
     */
    @Transactional
    public RosterPeriod createRosterPeriod(Long siteId, LocalDate startDate, Long previousPeriodId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> EntityNotFoundException.of("Site", siteId));

        RosterPeriod period = new RosterPeriod();
        period.setSite(site);
        period.setStartDate(startDate);
        period.setEndDate(startDate.plusDays(13));
        period.setStatus(RosterPeriodStatus.DRAFT);

        if (previousPeriodId == null) {
            period.setSequenceNumber(1);
        } else {
            RosterPeriod previous = requireRosterPeriod(previousPeriodId);

            if (previous.getStatus() != RosterPeriodStatus.SOLVED
                    && previous.getStatus() != RosterPeriodStatus.PUBLISHED) {
                throw new InvalidOperationException(
                        "A second roster period can only be created once period 1 is SOLVED or PUBLISHED. "
                        + "Period id=" + previousPeriodId + " is currently " + previous.getStatus() + ".");
            }

            LocalDate expectedStart = previous.getEndDate().plusDays(1);
            if (!startDate.equals(expectedStart)) {
                throw new InvalidOperationException(
                        "Period 2 start date must be " + expectedStart
                        + " (the day after period 1 ends). Provided: " + startDate);
            }

            period.setPreviousPeriod(previous);
            period.setSequenceNumber(2);
        }

        RosterPeriod saved = rosterPeriodRepository.save(period);
        log.info("Created RosterPeriod id={} site={} start={} sequence={}",
                saved.getId(), siteId, startDate, saved.getSequenceNumber());
        return saved;
    }

    /**
     * Reverts a roster period to {@code DRAFT} status.
     *
     * <p>This operation:</p>
     * <ol>
     *   <li>Unpins all {@link ShiftAssignment} rows belonging to this period.</li>
     *   <li>Sets the period status to {@code DRAFT}.</li>
     *   <li>If a following period exists (i.e. another period whose
     *       {@code previousPeriod} FK points to this one), and that following period
     *       is not {@code CANCELLED}, its assignments are cleared (staff set to null,
     *       pins removed) and it is also reverted to {@code DRAFT}.</li>
     * </ol>
     *
     * <p>Only periods in {@code SOLVED}, {@code PUBLISHED}, or {@code INFEASIBLE}
     * status may be reverted. A period in {@code SOLVING} status cannot be reverted
     * — cancel the active solve job first.</p>
     *
     * @throws EntityNotFoundException   if the roster period does not exist
     * @throws InvalidOperationException if the period is not in a revertable status
     */
    @Transactional
    public RosterPeriod revertToDraft(Long rosterPeriodId) {
        RosterPeriod period = requireRosterPeriod(rosterPeriodId);

        if (!REVERTABLE_STATUSES.contains(period.getStatus())) {
            throw new InvalidOperationException(
                    "RosterPeriod id=" + rosterPeriodId + " cannot be reverted to DRAFT. "
                    + "Current status: " + period.getStatus()
                    + ". If a solve is in progress, cancel it first.");
        }

        unpinPeriodAssignments(period);
        period.setStatus(RosterPeriodStatus.DRAFT);
        rosterPeriodRepository.save(period);

        rosterPeriodRepository.findByPreviousPeriod(period).ifPresent(following -> {
            if (following.getStatus() == RosterPeriodStatus.CANCELLED) {
                return;
            }
            log.info("Cascading revert to DRAFT: clearing assignments for following period id={}",
                    following.getId());
            clearPeriodAssignments(following);
            following.setStatus(RosterPeriodStatus.DRAFT);
            rosterPeriodRepository.save(following);
        });

        log.info("Reverted RosterPeriod id={} to DRAFT", rosterPeriodId);
        return rosterPeriodRepository.findById(rosterPeriodId).orElseThrow();
    }

    /**
     * Publishes a solved roster period.
     *
     * @throws EntityNotFoundException   if the roster period does not exist
     * @throws InvalidOperationException if the period is not in {@code SOLVED} status
     */
    @Transactional
    public RosterPeriod publish(Long rosterPeriodId) {
        RosterPeriod period = requireRosterPeriod(rosterPeriodId);

        if (period.getStatus() != RosterPeriodStatus.SOLVED) {
            throw new InvalidOperationException(
                    "Only a SOLVED period may be published. "
                    + "Period id=" + rosterPeriodId + " is " + period.getStatus() + ".");
        }

        period.setStatus(RosterPeriodStatus.PUBLISHED);
        period.setPublishedAt(OffsetDateTime.now());

        pinPeriodAssignments(period);

        log.info("Published RosterPeriod id={}", rosterPeriodId);
        return rosterPeriodRepository.save(period);
    }

    /**
     * Cancels a roster period.
     * A period in {@code SOLVING} status cannot be cancelled here — cancel the
     * active solve job via {@code SolverService} first.
     *
     * @throws EntityNotFoundException   if the roster period does not exist
     * @throws InvalidOperationException if the period is in {@code SOLVING} status
     */
    @Transactional
    public RosterPeriod cancel(Long rosterPeriodId) {
        RosterPeriod period = requireRosterPeriod(rosterPeriodId);

        if (period.getStatus() == RosterPeriodStatus.SOLVING) {
            throw new InvalidOperationException(
                    "RosterPeriod id=" + rosterPeriodId + " is currently being solved. "
                    + "Cancel the active solve job first.");
        }
        if (period.getStatus() == RosterPeriodStatus.CANCELLED) {
            throw new InvalidOperationException(
                    "RosterPeriod id=" + rosterPeriodId + " is already CANCELLED.");
        }

        period.setStatus(RosterPeriodStatus.CANCELLED);
        log.info("Cancelled RosterPeriod id={}", rosterPeriodId);
        return rosterPeriodRepository.save(period);
    }

    // =========================================================================
    // Shift management
    // =========================================================================

    /**
     * Returns all shifts in a roster period, ordered by start time.
     *
     * @throws EntityNotFoundException if the roster period does not exist
     */
    public List<Shift> getShifts(Long rosterPeriodId) {
        return shiftRepository.findByRosterPeriodOrderByStartDatetimeAsc(
                requireRosterPeriod(rosterPeriodId));
    }

    /**
     * Adds a shift to a roster period and automatically creates
     * {@code minimumStaff} unassigned {@link ShiftAssignment} slots.
     *
     * <p>If the period is in {@code SOLVED}, {@code PUBLISHED}, or {@code INFEASIBLE}
     * status, it is automatically reverted to {@code DRAFT}.</p>
     *
     * @throws EntityNotFoundException   if the roster period or shift type does not exist
     * @throws InvalidOperationException if the period is {@code CANCELLED} or {@code SOLVING},
     *                                   or if endDatetime is not after startDatetime,
     *                                   or if minimumStaff is less than 1
     */
    @Transactional
    public Shift addShift(Long rosterPeriodId, ShiftCreateRequest request) {
        RosterPeriod period = requireRosterPeriod(rosterPeriodId);
        requirePeriodModifiable(period);

        validateShiftDatetimes(request.startDatetime(), request.endDatetime());
        validateMinimumStaff(request.minimumStaff());

        Shift shift = new Shift();
        shift.setRosterPeriod(period);
        shift.setName(request.name());
        shift.setStartDatetime(request.startDatetime());
        shift.setEndDatetime(request.endDatetime());
        shift.setMinimumStaff(request.minimumStaff());
        shift.setNotes(request.notes());

        if (request.shiftTypeId() != null) {
            shift.setShiftType(requireShiftType(request.shiftTypeId()));
        }

        Shift saved = shiftRepository.save(shift);
        createSlots(saved, request.minimumStaff());

        revertToRequiredDraftIfNeeded(period);

        log.info("Added shift id={} to period id={} with {} slots",
                saved.getId(), rosterPeriodId, request.minimumStaff());
        return saved;
    }

    /**
     * Updates an existing shift. If {@code minimumStaff} changes, slots are reconciled:
     * <ul>
     *   <li>If increased: new unassigned slots are added.</li>
     *   <li>If decreased: unassigned, non-pinned slots are removed first. If the number
     *       of assigned or pinned slots already meets or exceeds the new minimum, no slots
     *       are removed — the slot count will remain above the new minimum.</li>
     * </ul>
     *
     * <p>If the period is in {@code SOLVED}, {@code PUBLISHED}, or {@code INFEASIBLE}
     * status, it is automatically reverted to {@code DRAFT}.</p>
     *
     * @throws EntityNotFoundException   if the shift or shift type does not exist
     * @throws InvalidOperationException if the period is {@code CANCELLED} or {@code SOLVING},
     *                                   or if endDatetime is not after startDatetime,
     *                                   or if minimumStaff is less than 1
     */
    @Transactional
    public Shift updateShift(Long shiftId, ShiftUpdateRequest request) {
        Shift shift = requireShift(shiftId);
        requirePeriodModifiable(shift.getRosterPeriod());

        validateShiftDatetimes(request.startDatetime(), request.endDatetime());
        validateMinimumStaff(request.minimumStaff());

        int previousMinimum = shift.getMinimumStaff();

        shift.setName(request.name());
        shift.setStartDatetime(request.startDatetime());
        shift.setEndDatetime(request.endDatetime());
        shift.setMinimumStaff(request.minimumStaff());
        shift.setNotes(request.notes());
        shift.setShiftType(request.shiftTypeId() != null ? requireShiftType(request.shiftTypeId()) : null);

        Shift saved = shiftRepository.save(shift);

        if (request.minimumStaff() != previousMinimum) {
            reconcileSlots(saved, request.minimumStaff());
        }

        revertToRequiredDraftIfNeeded(shift.getRosterPeriod());

        return saved;
    }

    /**
     * Removes a shift and all of its {@link ShiftAssignment} slots, including pinned ones.
     *
     * <p>If the period is in {@code SOLVED}, {@code PUBLISHED}, or {@code INFEASIBLE}
     * status, it is automatically reverted to {@code DRAFT}.</p>
     *
     * @throws EntityNotFoundException   if the shift does not exist
     * @throws InvalidOperationException if the period is {@code CANCELLED} or {@code SOLVING}
     */
    @Transactional
    public void removeShift(Long shiftId) {
        Shift shift = requireShift(shiftId);
        RosterPeriod period = shift.getRosterPeriod();
        requirePeriodModifiable(period);

        List<ShiftAssignment> slots = shiftAssignmentRepository.findByShift(shift);
        shiftAssignmentRepository.deleteAll(slots);
        shiftRepository.delete(shift);

        revertToRequiredDraftIfNeeded(period);

        log.info("Removed shift id={} and {} slots from period id={}",
                shiftId, slots.size(), period.getId());
    }

    // =========================================================================
    // ShiftQualificationRequirement management
    // =========================================================================

    /**
     * Adds a qualification requirement to a shift.
     *
     * @throws EntityNotFoundException   if the shift or qualification does not exist
     * @throws InvalidOperationException if a requirement for this qualification already exists
     *                                   on this shift, or minimumCount is less than 1
     */
    @Transactional
    public ShiftQualificationRequirement addQualificationRequirement(Long shiftId,
                                                                       Long qualificationId,
                                                                       int minimumCount) {
        Shift shift            = requireShift(shiftId);
        Qualification qual     = qualificationRepository.findById(qualificationId)
                .orElseThrow(() -> EntityNotFoundException.of("Qualification", qualificationId));

        if (minimumCount < 1) {
            throw new InvalidOperationException("minimumCount must be at least 1.");
        }
        if (shiftQualRequirementRepository.existsByShiftAndQualification(shift, qual)) {
            throw new InvalidOperationException(
                    "Shift id=" + shiftId + " already has a requirement for qualification id=" + qualificationId);
        }

        ShiftQualificationRequirement req = new ShiftQualificationRequirement();
        req.setShift(shift);
        req.setQualification(qual);
        req.setMinimumCount(minimumCount);
        return shiftQualRequirementRepository.save(req);
    }

    /**
     * Returns all qualification requirements for the given shift.
     *
     * @throws EntityNotFoundException if the shift does not exist
     */
    @Transactional(readOnly = true)
    public List<ShiftQualificationRequirement> getQualificationRequirements(Long shiftId) {
        return shiftQualRequirementRepository.findByShift(requireShift(shiftId));
    }

    /**
     * Removes a qualification requirement from a shift.
     *
     * @throws EntityNotFoundException if the requirement does not exist
     */
    @Transactional
    public void removeQualificationRequirement(Long requirementId) {
        ShiftQualificationRequirement req = shiftQualRequirementRepository.findById(requirementId)
                .orElseThrow(() -> EntityNotFoundException.of("ShiftQualificationRequirement", requirementId));
        shiftQualRequirementRepository.delete(req);
    }

    // =========================================================================
    // ShiftAssignment manual operations
    // =========================================================================

    /**
     * Returns all shift assignments for a roster period, including unassigned slots.
     *
     * @throws EntityNotFoundException if the roster period does not exist
     */
    public List<ShiftAssignment> getAssignments(Long rosterPeriodId) {
        return shiftAssignmentRepository.findByRosterPeriod(requireRosterPeriod(rosterPeriodId));
    }

    /**
     * Manually assigns a staff member to a specific slot.
     * The slot must not be pinned.
     *
     * @throws EntityNotFoundException   if the assignment or staff member does not exist
     * @throws InvalidOperationException if the slot is pinned
     */
    @Transactional
    public ShiftAssignment assignStaff(Long assignmentId, Long staffId) {
        ShiftAssignment assignment = requireAssignment(assignmentId);

        if (assignment.isPinned()) {
            throw new InvalidOperationException(
                    "ShiftAssignment id=" + assignmentId + " is pinned and cannot be changed. "
                    + "Unpin it first.");
        }

        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> EntityNotFoundException.of("Staff", staffId));
        assignment.setStaff(staff);
        return shiftAssignmentRepository.save(assignment);
    }

    /**
     * Clears the staff assignment from a slot, returning it to an unassigned state.
     * The slot must not be pinned.
     *
     * @throws EntityNotFoundException   if the assignment does not exist
     * @throws InvalidOperationException if the slot is pinned
     */
    @Transactional
    public ShiftAssignment clearAssignment(Long assignmentId) {
        ShiftAssignment assignment = requireAssignment(assignmentId);

        if (assignment.isPinned()) {
            throw new InvalidOperationException(
                    "ShiftAssignment id=" + assignmentId + " is pinned and cannot be cleared. "
                    + "Unpin it first.");
        }

        assignment.setStaff(null);
        return shiftAssignmentRepository.save(assignment);
    }

    /**
     * Pins a shift assignment so the solver cannot change it.
     *
     * @throws EntityNotFoundException if the assignment does not exist
     */
    @Transactional
    public ShiftAssignment pin(Long assignmentId) {
        ShiftAssignment assignment = requireAssignment(assignmentId);
        assignment.setPinned(true);
        return shiftAssignmentRepository.save(assignment);
    }

    /**
     * Unpins a shift assignment, allowing the solver to reassign it.
     *
     * @throws EntityNotFoundException if the assignment does not exist
     */
    @Transactional
    public ShiftAssignment unpin(Long assignmentId) {
        ShiftAssignment assignment = requireAssignment(assignmentId);
        assignment.setPinned(false);
        return shiftAssignmentRepository.save(assignment);
    }

    // =========================================================================
    // Private helpers — period state management
    // =========================================================================

    /**
     * Asserts that the given period permits shift modifications.
     *
     * @throws InvalidOperationException if the period is CANCELLED or SOLVING
     */
    private void requirePeriodModifiable(RosterPeriod period) {
        if (!MODIFIABLE_STATUSES.contains(period.getStatus())) {
            throw new InvalidOperationException(
                    "RosterPeriod id=" + period.getId() + " does not allow modifications. "
                    + "Status: " + period.getStatus() + ".");
        }
    }

    /**
     * If the period is in a non-DRAFT but modifiable status (SOLVED, PUBLISHED, INFEASIBLE),
     * reverts it to DRAFT with cascade. DRAFT periods are left unchanged.
     */
    private void revertToRequiredDraftIfNeeded(RosterPeriod period) {
        if (period.getStatus() == RosterPeriodStatus.DRAFT) {
            return;
        }
        log.info("Shift modification on period id={} (status={}). Reverting to DRAFT.",
                period.getId(), period.getStatus());
        revertToDraft(period.getId());
    }

    // =========================================================================
    // Private helpers — slot management
    // =========================================================================

    /**
     * Creates {@code count} unassigned, non-pinned {@link ShiftAssignment} slots
     * for the given shift.
     */
    private void createSlots(Shift shift, int count) {
        List<ShiftAssignment> slots = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ShiftAssignment slot = new ShiftAssignment();
            slot.setShift(shift);
            slot.setStaff(null);
            slot.setPinned(false);
            slots.add(slot);
        }
        shiftAssignmentRepository.saveAll(slots);
    }

    /**
     * Reconciles the slot count for a shift after {@code minimumStaff} changes.
     *
     * <p>If the count increased: adds new empty slots to reach the new minimum.</p>
     * <p>If the count decreased: removes unassigned, non-pinned slots until the slot
     * total equals {@code Math.max(newMinimum, assignedOrPinnedCount)}. Occupied or
     * pinned slots are never removed automatically.</p>
     */
    private void reconcileSlots(Shift shift, int newMinimum) {
        List<ShiftAssignment> currentSlots = shiftAssignmentRepository.findByShift(shift);
        int currentCount = currentSlots.size();

        if (newMinimum > currentCount) {
            createSlots(shift, newMinimum - currentCount);
            return;
        }

        if (newMinimum < currentCount) {
            long assignedOrPinned = currentSlots.stream()
                    .filter(s -> s.getStaff() != null || s.isPinned())
                    .count();

            int targetCount  = (int) Math.max(newMinimum, assignedOrPinned);
            int slotsToRemove = currentCount - targetCount;

            if (slotsToRemove <= 0) {
                return;
            }

            List<ShiftAssignment> removable = currentSlots.stream()
                    .filter(s -> s.getStaff() == null && !s.isPinned())
                    .limit(slotsToRemove)
                    .collect(Collectors.toList());

            shiftAssignmentRepository.deleteAll(removable);
            log.debug("Reconciled slots for shift id={}: removed {}, target count={}",
                    shift.getId(), removable.size(), targetCount);
        }
    }

    /**
     * Pins all assigned slots in a roster period.
     * Called when a period is published so that period 2 solving treats
     * period 1 assignments as fixed problem facts.
     */
    private void pinPeriodAssignments(RosterPeriod period) {
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByRosterPeriod(period);
        assignments.stream()
                .filter(a -> a.getStaff() != null)
                .forEach(a -> a.setPinned(true));
        shiftAssignmentRepository.saveAll(assignments);
    }

    /**
     * Unpins all slots in a roster period.
     * Called when reverting a period to DRAFT for replanning.
     */
    private void unpinPeriodAssignments(RosterPeriod period) {
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByRosterPeriod(period);
        assignments.forEach(a -> a.setPinned(false));
        shiftAssignmentRepository.saveAll(assignments);
    }

    /**
     * Clears all assignments in a roster period: sets staff to null and removes all pins.
     * Called when cascading a period-1 revert to period 2.
     */
    private void clearPeriodAssignments(RosterPeriod period) {
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByRosterPeriod(period);
        assignments.forEach(a -> {
            a.setStaff(null);
            a.setPinned(false);
        });
        shiftAssignmentRepository.saveAll(assignments);
    }

    // =========================================================================
    // Private helpers — validation
    // =========================================================================

    private void validateShiftDatetimes(OffsetDateTime start, OffsetDateTime end) {
        if (!end.isAfter(start)) {
            throw new InvalidOperationException(
                    "Shift end datetime must be after start datetime.");
        }
    }

    private void validateMinimumStaff(int minimumStaff) {
        if (minimumStaff < 1) {
            throw new InvalidOperationException("minimumStaff must be at least 1.");
        }
    }

    // =========================================================================
    // Private helpers — entity lookups
    // =========================================================================

    private RosterPeriod requireRosterPeriod(Long id) {
        return rosterPeriodRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("RosterPeriod", id));
    }

    private Shift requireShift(Long id) {
        return shiftRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Shift", id));
    }

    private ShiftAssignment requireAssignment(Long id) {
        return shiftAssignmentRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("ShiftAssignment", id));
    }

    private ShiftType requireShiftType(Long id) {
        return shiftTypeRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("ShiftType", id));
    }
}
