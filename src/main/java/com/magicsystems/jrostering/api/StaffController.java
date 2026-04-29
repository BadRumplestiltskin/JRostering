package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.service.StaffAssignmentService;
import com.magicsystems.jrostering.service.StaffQualificationService;
import com.magicsystems.jrostering.service.StaffRelationshipService;
import com.magicsystems.jrostering.service.StaffService;
import com.magicsystems.jrostering.service.StaffService.StaffCreateRequest;
import com.magicsystems.jrostering.service.StaffService.StaffUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Tag(name = "Staff", description = "Staff CRUD, qualifications, site assignments, availability, preferences and leave")
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class StaffController {

    private final StaffService             staffService;
    private final StaffQualificationService qualificationService;
    private final StaffAssignmentService    assignmentService;
    private final StaffRelationshipService  relationshipService;

    // =========================================================================
    // Staff CRUD
    // =========================================================================

    @Operation(summary = "Get a staff member by ID")
    @GetMapping("/{staffId}")
    public ResponseEntity<Staff> getStaff(@PathVariable Long staffId) {
        return ResponseEntity.ok(staffService.getById(staffId));
    }

    @Operation(summary = "List all active staff for an organisation")
    @GetMapping("/organisations/{organisationId}")
    public ResponseEntity<List<Staff>> getStaffByOrganisation(@PathVariable Long organisationId) {
        return ResponseEntity.ok(staffService.getAllActiveByOrganisation(organisationId));
    }

    @Operation(summary = "Create a staff member")
    @PostMapping("/organisations/{organisationId}")
    public ResponseEntity<Staff> createStaff(
            @PathVariable Long organisationId,
            @RequestBody StaffCreateRequest request) {

        return ResponseEntity.ok(staffService.create(organisationId, request));
    }

    @Operation(summary = "Update a staff member")
    @PutMapping("/{staffId}")
    public ResponseEntity<Staff> updateStaff(
            @PathVariable Long staffId,
            @RequestBody StaffUpdateRequest request) {

        return ResponseEntity.ok(staffService.update(staffId, request));
    }

    @Operation(summary = "Deactivate (soft-delete) a staff member")
    @DeleteMapping("/{staffId}")
    public ResponseEntity<Staff> deactivateStaff(@PathVariable Long staffId) {
        return ResponseEntity.ok(staffService.deactivate(staffId));
    }

    // =========================================================================
    // Qualification management
    // =========================================================================

    @Operation(summary = "Add a qualification to a staff member")
    @PostMapping("/{staffId}/qualifications/{qualificationId}")
    public ResponseEntity<StaffQualification> addQualification(
            @PathVariable Long staffId,
            @PathVariable Long qualificationId,
            @RequestParam(required = false) LocalDate awardedDate) {

        return ResponseEntity.ok(qualificationService.addQualification(staffId, qualificationId, awardedDate));
    }

    @Operation(summary = "Remove a qualification from a staff member")
    @DeleteMapping("/{staffId}/qualifications/{qualificationId}")
    public ResponseEntity<Void> removeQualification(
            @PathVariable Long staffId,
            @PathVariable Long qualificationId) {

        qualificationService.removeQualification(staffId, qualificationId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Site assignment management
    // =========================================================================

    @Operation(summary = "Assign a staff member to a site")
    @PostMapping("/{staffId}/sites/{siteId}")
    public ResponseEntity<StaffSiteAssignment> addSiteAssignment(
            @PathVariable Long staffId,
            @PathVariable Long siteId,
            @RequestParam(defaultValue = "false") boolean primarySite) {

        return ResponseEntity.ok(assignmentService.addSiteAssignment(staffId, siteId, primarySite));
    }

    @Operation(summary = "Remove a staff member's site assignment")
    @DeleteMapping("/{staffId}/sites/{siteId}")
    public ResponseEntity<Void> removeSiteAssignment(
            @PathVariable Long staffId,
            @PathVariable Long siteId) {

        assignmentService.removeSiteAssignment(staffId, siteId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Incompatibility management
    // =========================================================================

    @Operation(summary = "Record two staff members as incompatible (must not share a shift)")
    @PostMapping("/{staffIdOne}/incompatibilities/{staffIdTwo}")
    public ResponseEntity<StaffIncompatibility> addIncompatibility(
            @PathVariable Long staffIdOne,
            @PathVariable Long staffIdTwo,
            @RequestParam(required = false) String reason) {

        return ResponseEntity.ok(relationshipService.addIncompatibility(staffIdOne, staffIdTwo, reason));
    }

    @Operation(summary = "Remove an incompatibility record between two staff members")
    @DeleteMapping("/{staffIdOne}/incompatibilities/{staffIdTwo}")
    public ResponseEntity<Void> removeIncompatibility(
            @PathVariable Long staffIdOne,
            @PathVariable Long staffIdTwo) {

        relationshipService.removeIncompatibility(staffIdOne, staffIdTwo);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Pairing management
    // =========================================================================

    @Operation(summary = "Record two staff members as a required pair (must always share a shift)")
    @PostMapping("/{staffIdOne}/pairings/{staffIdTwo}")
    public ResponseEntity<StaffPairing> addPairing(
            @PathVariable Long staffIdOne,
            @PathVariable Long staffIdTwo,
            @RequestParam(required = false) String reason) {

        return ResponseEntity.ok(relationshipService.addPairing(staffIdOne, staffIdTwo, reason));
    }

    @Operation(summary = "Remove a pairing record between two staff members")
    @DeleteMapping("/{staffIdOne}/pairings/{staffIdTwo}")
    public ResponseEntity<Void> removePairing(
            @PathVariable Long staffIdOne,
            @PathVariable Long staffIdTwo) {

        relationshipService.removePairing(staffIdOne, staffIdTwo);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Availability management
    // =========================================================================

    @Operation(summary = "Get availability windows for a staff member")
    @GetMapping("/{staffId}/availability")
    public ResponseEntity<List<StaffAvailability>> getAvailability(@PathVariable Long staffId) {
        return ResponseEntity.ok(staffService.getAvailability(staffId));
    }

    @Operation(summary = "Add an availability window for a staff member")
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

    @Operation(summary = "Remove an availability window")
    @DeleteMapping("/availability/{availabilityId}")
    public ResponseEntity<Void> removeAvailability(@PathVariable Long availabilityId) {
        staffService.removeAvailability(availabilityId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Preference management
    // =========================================================================

    @Operation(summary = "Get scheduling preferences for a staff member")
    @GetMapping("/{staffId}/preferences")
    public ResponseEntity<List<StaffPreference>> getPreferences(@PathVariable Long staffId) {
        return ResponseEntity.ok(staffService.getPreferences(staffId));
    }

    @Operation(summary = "Add a scheduling preference for a staff member")
    @PostMapping("/{staffId}/preferences")
    public ResponseEntity<StaffPreference> addPreference(
            @PathVariable Long staffId,
            @RequestParam PreferenceType preferenceType,
            @RequestParam(required = false) DayOfWeek dayOfWeek,
            @RequestParam(required = false) Long shiftTypeId) {

        return ResponseEntity.ok(
                staffService.addPreference(staffId, preferenceType, dayOfWeek, shiftTypeId));
    }

    @Operation(summary = "Remove a scheduling preference")
    @DeleteMapping("/preferences/{preferenceId}")
    public ResponseEntity<Void> removePreference(@PathVariable Long preferenceId) {
        staffService.removePreference(preferenceId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Leave management
    // =========================================================================

    @Operation(summary = "Get leave records for a staff member")
    @GetMapping("/{staffId}/leave")
    public ResponseEntity<List<Leave>> getLeave(@PathVariable Long staffId) {
        return ResponseEntity.ok(staffService.getLeave(staffId));
    }

    @Operation(summary = "Request leave for a staff member")
    @PostMapping("/{staffId}/leave")
    public ResponseEntity<Leave> addLeave(
            @PathVariable Long staffId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam LeaveType leaveType,
            @RequestParam(required = false) String notes) {

        return ResponseEntity.ok(staffService.addLeave(staffId, startDate, endDate, leaveType, notes));
    }

    @Operation(summary = "Approve or reject a leave request")
    @PutMapping("/leave/{leaveId}/status")
    public ResponseEntity<Leave> updateLeaveStatus(
            @PathVariable Long leaveId,
            @RequestParam LeaveStatus status) {

        return ResponseEntity.ok(staffService.updateLeaveStatus(leaveId, status));
    }

    @Operation(summary = "Cancel and remove a leave request")
    @DeleteMapping("/leave/{leaveId}")
    public ResponseEntity<Void> removeLeave(@PathVariable Long leaveId) {
        staffService.removeLeave(leaveId);
        return ResponseEntity.noContent().build();
    }
}
