package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.domain.RosterPeriod;
import com.magicsystems.jrostering.domain.Shift;
import com.magicsystems.jrostering.domain.ShiftAssignment;
import com.magicsystems.jrostering.service.RosterService;
import com.magicsystems.jrostering.service.RosterService.ShiftCreateRequest;
import com.magicsystems.jrostering.service.RosterService.ShiftUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API for roster period management, shift operations, and assignment management.
 *
 * <p>All endpoints require HTTP Basic authentication and are under {@code /api/roster}.</p>
 */
@RestController
@RequestMapping("/api/roster")
@RequiredArgsConstructor
public class RosterController {

    private final RosterService rosterService;

    // =========================================================================
    // Roster period endpoints
    // =========================================================================

    @GetMapping("/periods/{periodId}")
    public ResponseEntity<RosterPeriod> getPeriod(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.getById(periodId));
    }

    @GetMapping("/sites/{siteId}/periods")
    public ResponseEntity<List<RosterPeriod>> getPeriodsForSite(@PathVariable Long siteId) {
        return ResponseEntity.ok(rosterService.getBySite(siteId));
    }

    @PostMapping("/sites/{siteId}/periods")
    public ResponseEntity<RosterPeriod> createPeriod(
            @PathVariable Long siteId,
            @RequestParam LocalDate startDate,
            @RequestParam(required = false) Long previousPeriodId) {

        return ResponseEntity.ok(rosterService.createRosterPeriod(siteId, startDate, previousPeriodId));
    }

    @PostMapping("/periods/{periodId}/revert-to-draft")
    public ResponseEntity<RosterPeriod> revertToDraft(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.revertToDraft(periodId));
    }

    @PostMapping("/periods/{periodId}/publish")
    public ResponseEntity<RosterPeriod> publish(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.publish(periodId));
    }

    @PostMapping("/periods/{periodId}/cancel")
    public ResponseEntity<RosterPeriod> cancel(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.cancel(periodId));
    }

    // =========================================================================
    // Shift endpoints
    // =========================================================================

    @GetMapping("/periods/{periodId}/shifts")
    public ResponseEntity<List<Shift>> getShifts(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.getShifts(periodId));
    }

    @PostMapping("/periods/{periodId}/shifts")
    public ResponseEntity<Shift> addShift(
            @PathVariable Long periodId,
            @RequestBody ShiftCreateRequest request) {

        return ResponseEntity.ok(rosterService.addShift(periodId, request));
    }

    @PutMapping("/shifts/{shiftId}")
    public ResponseEntity<Shift> updateShift(
            @PathVariable Long shiftId,
            @RequestBody ShiftUpdateRequest request) {

        return ResponseEntity.ok(rosterService.updateShift(shiftId, request));
    }

    @DeleteMapping("/shifts/{shiftId}")
    public ResponseEntity<Void> removeShift(@PathVariable Long shiftId) {
        rosterService.removeShift(shiftId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Assignment endpoints
    // =========================================================================

    @GetMapping("/periods/{periodId}/assignments")
    public ResponseEntity<List<ShiftAssignment>> getAssignments(@PathVariable Long periodId) {
        return ResponseEntity.ok(rosterService.getAssignments(periodId));
    }

    @PostMapping("/assignments/{assignmentId}/assign/{staffId}")
    public ResponseEntity<ShiftAssignment> assignStaff(
            @PathVariable Long assignmentId,
            @PathVariable Long staffId) {

        return ResponseEntity.ok(rosterService.assignStaff(assignmentId, staffId));
    }

    @DeleteMapping("/assignments/{assignmentId}/assign")
    public ResponseEntity<ShiftAssignment> clearAssignment(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(rosterService.clearAssignment(assignmentId));
    }

    @PostMapping("/assignments/{assignmentId}/pin")
    public ResponseEntity<ShiftAssignment> pin(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(rosterService.pin(assignmentId));
    }

    @DeleteMapping("/assignments/{assignmentId}/pin")
    public ResponseEntity<ShiftAssignment> unpin(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(rosterService.unpin(assignmentId));
    }
}
