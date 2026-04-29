package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.domain.RosterPeriod;
import com.magicsystems.jrostering.domain.Shift;
import com.magicsystems.jrostering.domain.ShiftAssignment;
import com.magicsystems.jrostering.service.RosterService;
import com.magicsystems.jrostering.service.RosterService.ShiftCreateRequest;
import com.magicsystems.jrostering.service.RosterService.ShiftUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Roster", description = "Roster period lifecycle, shift management and assignment pinning")
@RestController
@RequestMapping("/api/roster")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class RosterController {

    private final RosterService rosterService;

    // =========================================================================
    // Roster period endpoints
    // =========================================================================

    @Operation(summary = "Get a roster period by ID")
    @GetMapping("/periods/{periodId}")
    public ResponseEntity<RosterPeriod> getPeriod(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.getById(periodId));
    }

    @Operation(summary = "List roster periods for a site")
    @GetMapping("/sites/{siteId}/periods")
    public ResponseEntity<List<RosterPeriod>> getPeriodsForSite(@PathVariable Long siteId) {
        return ResponseEntity.ok(rosterService.getBySite(siteId));
    }

    @Operation(summary = "Create a roster period for a site")
    @PostMapping("/sites/{siteId}/periods")
    public ResponseEntity<RosterPeriod> createPeriod(
            @PathVariable Long siteId,
            @RequestParam LocalDate startDate,
            @RequestParam(required = false) Long previousPeriodId) {

        return ResponseEntity.ok(rosterService.createRosterPeriod(siteId, startDate, previousPeriodId));
    }

    @Operation(summary = "Revert a solved or published period back to DRAFT")
    @PostMapping("/periods/{periodId}/revert-to-draft")
    public ResponseEntity<RosterPeriod> revertToDraft(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.revertToDraft(periodId));
    }

    @Operation(summary = "Publish a solved roster period")
    @PostMapping("/periods/{periodId}/publish")
    public ResponseEntity<RosterPeriod> publish(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.publish(periodId));
    }

    @Operation(summary = "Cancel a roster period")
    @PostMapping("/periods/{periodId}/cancel")
    public ResponseEntity<RosterPeriod> cancel(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.cancel(periodId));
    }

    // =========================================================================
    // Shift endpoints
    // =========================================================================

    @Operation(summary = "List shifts in a roster period")
    @GetMapping("/periods/{periodId}/shifts")
    public ResponseEntity<List<Shift>> getShifts(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.getShifts(periodId));
    }

    @Operation(summary = "Add a shift to a roster period")
    @PostMapping("/periods/{periodId}/shifts")
    public ResponseEntity<Shift> addShift(
            @PathVariable Long periodId,
            @RequestBody ShiftCreateRequest request) {

        return ResponseEntity.ok(rosterService.addShift(periodId, request));
    }

    @Operation(summary = "Update a shift")
    @PutMapping("/shifts/{shiftId}")
    public ResponseEntity<Shift> updateShift(
            @PathVariable Long shiftId,
            @RequestBody ShiftUpdateRequest request) {

        return ResponseEntity.ok(rosterService.updateShift(shiftId, request));
    }

    @Operation(summary = "Remove a shift from a roster period")
    @DeleteMapping("/shifts/{shiftId}")
    public ResponseEntity<Void> removeShift(@PathVariable Long shiftId) {
        rosterService.removeShift(shiftId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Assignment endpoints
    // =========================================================================

    @Operation(summary = "List all shift assignments for a roster period")
    @GetMapping("/periods/{periodId}/assignments")
    public ResponseEntity<List<ShiftAssignment>> getAssignments(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.getAssignments(periodId));
    }

    @Operation(summary = "Manually assign a staff member to an assignment slot")
    @PostMapping("/assignments/{assignmentId}/assign/{staffId}")
    public ResponseEntity<ShiftAssignment> assignStaff(
            @PathVariable Long assignmentId,
            @PathVariable Long staffId) {

        return ResponseEntity.ok(rosterService.assignStaff(assignmentId, staffId));
    }

    @Operation(summary = "Clear the staff assignment from a slot")
    @DeleteMapping("/assignments/{assignmentId}/assign")
    public ResponseEntity<ShiftAssignment> clearAssignment(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(rosterService.clearAssignment(assignmentId));
    }

    @Operation(summary = "Pin an assignment so the solver cannot change it")
    @PostMapping("/assignments/{assignmentId}/pin")
    public ResponseEntity<ShiftAssignment> pin(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(rosterService.pin(assignmentId));
    }

    @Operation(summary = "Unpin an assignment so the solver can reassign it")
    @DeleteMapping("/assignments/{assignmentId}/pin")
    public ResponseEntity<ShiftAssignment> unpin(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(rosterService.unpin(assignmentId));
    }
}
