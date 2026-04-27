package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.service.StaffService;
import com.magicsystems.jrostering.service.StaffService.StaffCreateRequest;
import com.magicsystems.jrostering.service.StaffService.StaffUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * REST API for staff management: CRUD, qualifications, site assignments,
 * incompatibilities, pairings, availability, preferences, and leave.
 *
 * <p>All endpoints require HTTP Basic authentication and are under {@code /api/staff}.</p>
 */
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    // =========================================================================
    // Staff CRUD
    // =========================================================================

    @GetMapping("/{staffId}")
    public ResponseEntity<Staff> getStaff(@PathVariable Long staffId) {
        return ResponseEntity.ok(staffService.getById(staffId));
    }

    @GetMapping("/organisations/{organisationId}")
    public ResponseEntity<List<Staff>> getStaffByOrganisation(@PathVariable Long organisationId) {
        return ResponseEntity.ok(staffService.getAllActiveByOrganisation(organisationId));
    }

    @PostMapping("/organisations/{organisationId}")
    public ResponseEntity<Staff> createStaff(
            @PathVariable Long organisationId,
            @RequestBody StaffCreateRequest request) {

        return ResponseEntity.ok(staffService.create(organisationId, request));
    }

    @PutMapping("/{staffId}")
    public ResponseEntity<Staff> updateStaff(
            @PathVariable Long staffId,
            @RequestBody StaffUpdateRequest request) {

        return ResponseEntity.ok(staffService.update(staffId, request));
    }

    @DeleteMapping("/{staffId}")
    public ResponseEntity<Staff> deactivateStaff(@PathVariable Long staffId) {
        return ResponseEntity.ok(staffService.deactivate(staffId));
    }

    // =========================================================================
    // Qualification management
    // =========================================================================

    @PostMapping("/{staffId}/qualifications/{qualificationId}")
    public ResponseEntity<StaffQualification> addQualification(
            @PathVariable Long staffId,
            @PathVariable Long qualificationId,
            @RequestParam(required = false) LocalDate awardedDate) {

        return ResponseEntity.ok(staffService.addQualification(staffId, qualificationId, awardedDate));
    }

    @DeleteMapping("/{staffId}/qualifications/{qualificationId}")
    public ResponseEntity<Void> removeQualification(
            @PathVariable Long staffId,
            @PathVariable Long qualificationId) {

        staffService.removeQualification(staffId, qualificationId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Site assignment management
    // =========================================================================

    @PostMapping("/{staffId}/sites/{siteId}")
    public ResponseEntity<StaffSiteAssignment> addSiteAssignment(
            @PathVariable Long staffId,
            @PathVariable Long siteId,
            @RequestParam(defaultValue = "false") boolean primarySite) {

        return ResponseEntity.ok(staffService.addSiteAssignment(staffId, siteId, primarySite));
    }

    @DeleteMapping("/{staffId}/sites/{siteId}")
    public ResponseEntity<Void> removeSiteAssignment(
            @PathVariable Long staffId,
            @PathVariable Long siteId) {

        staffService.removeSiteAssignment(staffId, siteId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Incompatibility management
    // =========================================================================

    @PostMapping("/{staffIdOne}/incompatibilities/{staffIdTwo}")
    public ResponseEntity<StaffIncompatibility> addIncompatibility(
            @PathVariable Long staffIdOne,
            @PathVariable Long staffIdTwo,
            @RequestParam(required = false) String reason) {

        return ResponseEntity.ok(staffService.addIncompatibility(staffIdOne, staffIdTwo, reason));
    }

    @DeleteMapping("/{staffIdOne}/incompatibilities/{staffIdTwo}")
    public ResponseEntity<Void> removeIncompatibility(
            @PathVariable Long staffIdOne,
            @PathVariable Long staffIdTwo) {

        staffService.removeIncompatibility(staffIdOne, staffIdTwo);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Pairing management
    // =========================================================================

    @PostMapping("/{staffIdOne}/pairings/{staffIdTwo}")
    public ResponseEntity<StaffPairing> addPairing(
            @PathVariable Long staffIdOne,
            @PathVariable Long staffIdTwo,
            @RequestParam(required = false) String reason) {

        return ResponseEntity.ok(staffService.addPairing(staffIdOne, staffIdTwo, reason));
    }

    @DeleteMapping("/{staffIdOne}/pairings/{staffIdTwo}")
    public ResponseEntity<Void> removePairing(
            @PathVariable Long staffIdOne,
            @PathVariable Long staffIdTwo) {

        staffService.removePairing(staffIdOne, staffIdTwo);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Availability management
    // =========================================================================

    @GetMapping("/{staffId}/availability")
    public ResponseEntity<List<StaffAvailability>> getAvailability(@PathVariable Long staffId) {
        return ResponseEntity.ok(staffService.getAvailability(staffId));
    }

    @PostMapping("/{staffId}/availability")
    public ResponseEntity<StaffAvailability> addAvailability(
            @PathVariable Long staffId,
            @RequestParam DayOfWeek dayOfWeek,
            @RequestParam LocalTime startTime,
            @RequestParam LocalTime endTime,
            @RequestParam boolean available) {

        return ResponseEntity.ok(
                staffService.addAvailability(staffId, dayOfWeek, startTime, endTime, available));
    }

    @DeleteMapping("/availability/{availabilityId}")
    public ResponseEntity<Void> removeAvailability(@PathVariable Long availabilityId) {
        staffService.removeAvailability(availabilityId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Preference management
    // =========================================================================

    @GetMapping("/{staffId}/preferences")
    public ResponseEntity<List<StaffPreference>> getPreferences(@PathVariable Long staffId) {
        return ResponseEntity.ok(staffService.getPreferences(staffId));
    }

    @PostMapping("/{staffId}/preferences")
    public ResponseEntity<StaffPreference> addPreference(
            @PathVariable Long staffId,
            @RequestParam PreferenceType preferenceType,
            @RequestParam(required = false) DayOfWeek dayOfWeek,
            @RequestParam(required = false) Long shiftTypeId) {

        return ResponseEntity.ok(
                staffService.addPreference(staffId, preferenceType, dayOfWeek, shiftTypeId));
    }

    @DeleteMapping("/preferences/{preferenceId}")
    public ResponseEntity<Void> removePreference(@PathVariable Long preferenceId) {
        staffService.removePreference(preferenceId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Leave management
    // =========================================================================

    @GetMapping("/{staffId}/leave")
    public ResponseEntity<List<Leave>> getLeave(@PathVariable Long staffId) {
        return ResponseEntity.ok(staffService.getLeave(staffId));
    }

    @PostMapping("/{staffId}/leave")
    public ResponseEntity<Leave> addLeave(
            @PathVariable Long staffId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam LeaveType leaveType,
            @RequestParam(required = false) String notes) {

        return ResponseEntity.ok(staffService.addLeave(staffId, startDate, endDate, leaveType, notes));
    }

    @PutMapping("/leave/{leaveId}/status")
    public ResponseEntity<Leave> updateLeaveStatus(
            @PathVariable Long leaveId,
            @RequestParam LeaveStatus status) {

        return ResponseEntity.ok(staffService.updateLeaveStatus(leaveId, status));
    }

    @DeleteMapping("/leave/{leaveId}")
    public ResponseEntity<Void> removeLeave(@PathVariable Long leaveId) {
        staffService.removeLeave(leaveId);
        return ResponseEntity.noContent().build();
    }
}
