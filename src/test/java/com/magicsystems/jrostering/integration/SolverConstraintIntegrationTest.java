package com.magicsystems.jrostering.integration;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.OrganisationRepository;
import com.magicsystems.jrostering.service.*;
import com.magicsystems.jrostering.service.RosterService.ShiftCreateRequest;
import com.magicsystems.jrostering.service.SiteService.SiteCreateRequest;
import com.magicsystems.jrostering.service.StaffService.StaffCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying that multi-constraint solver scenarios behave correctly
 * end-to-end against a real PostgreSQL container.
 *
 * <p>These tests do NOT use {@code @Transactional} because the solver runs on a
 * background thread and needs to see committed data.</p>
 */
class SolverConstraintIntegrationTest extends IntegrationTestBase {

    private static final int  SOLVE_LIMIT_SECONDS = 5;
    private static final long POLL_INTERVAL_MS    = 500;
    private static final long POLL_TIMEOUT_MS     = 30_000;

    @Autowired OrganisationRepository  organisationRepository;
    @Autowired SiteService             siteService;
    @Autowired StaffService            staffService;
    @Autowired StaffAssignmentService  assignmentService;
    @Autowired StaffRelationshipService relationshipService;
    @Autowired RosterService           rosterService;
    @Autowired SolverService           solverService;

    // =========================================================================
    // Incompatible pair — forced onto same shift
    // =========================================================================

    /**
     * Two staff members marked as incompatible are the only available staff for a
     * shift requiring 2 slots. STAFF_MUTUAL_EXCLUSION is HARD by default, so the
     * solver must report INFEASIBLE (it cannot satisfy both "fill 2 slots" and
     * "incompatible pair never on same shift" simultaneously).
     */
    @Test
    void incompatiblePair_infeasible_whenBothRequiredOnSameShift() throws InterruptedException {
        Organisation org = organisationRepository.findAll().getFirst();

        Site site = siteService.create(org.getId(),
                new SiteCreateRequest("Incompatibility Test Site", "UTC", null));

        Staff staffA = staffService.create(org.getId(), new StaffCreateRequest(
                "Incompat", "A", "incompat-a@test.invalid", null,
                EmploymentType.FULL_TIME, null, null));
        Staff staffB = staffService.create(org.getId(), new StaffCreateRequest(
                "Incompat", "B", "incompat-b@test.invalid", null,
                EmploymentType.FULL_TIME, null, null));

        assignmentService.addSiteAssignment(staffA.getId(), site.getId(), true);
        assignmentService.addSiteAssignment(staffB.getId(), site.getId(), true);

        // Mark A and B as mutually incompatible — STAFF_MUTUAL_EXCLUSION is HARD
        relationshipService.addIncompatibility(staffA.getId(), staffB.getId(), "test incompatibility");

        LocalDate date = LocalDate.now().plusDays(1);
        RosterPeriod period = rosterService.createRosterPeriod(site.getId(), date, null);

        OffsetDateTime shiftStart = date.atTime(8, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime shiftEnd   = date.atTime(16, 0).atOffset(ZoneOffset.UTC);

        // 1 shift requiring 2 staff — both incompatible staff must fill it → INFEASIBLE
        rosterService.addShift(period.getId(), new ShiftCreateRequest(
                null, "Day", shiftStart, shiftEnd, 2, null));

        SolverJob job = solverService.submitSolve(period.getId(), SOLVE_LIMIT_SECONDS);
        SolverJobStatus finalStatus = awaitTerminal(job.getId());

        assertThat(finalStatus)
                .as("Should be INFEASIBLE: 2 incompatible staff cannot share one 2-slot shift")
                .isEqualTo(SolverJobStatus.INFEASIBLE);
    }

    // =========================================================================
    // Incompatible pair — alternative staff available
    // =========================================================================

    /**
     * Three staff where A and B are incompatible. One shift needs 2 staff.
     * The solver should choose A+C or B+C, never A+B.
     * Result must be COMPLETED with no HARD violations.
     */
    @Test
    void incompatiblePair_completed_whenAlternativeStaffAvailable() throws InterruptedException {
        Organisation org = organisationRepository.findAll().getFirst();

        Site site = siteService.create(org.getId(),
                new SiteCreateRequest("Incompatibility Alt Test Site", "UTC", null));

        Staff staffA = staffService.create(org.getId(), new StaffCreateRequest(
                "Alt", "A", "alt-a@test.invalid", null,
                EmploymentType.FULL_TIME, null, null));
        Staff staffB = staffService.create(org.getId(), new StaffCreateRequest(
                "Alt", "B", "alt-b@test.invalid", null,
                EmploymentType.FULL_TIME, null, null));
        Staff staffC = staffService.create(org.getId(), new StaffCreateRequest(
                "Alt", "C", "alt-c@test.invalid", null,
                EmploymentType.FULL_TIME, null, null));

        assignmentService.addSiteAssignment(staffA.getId(), site.getId(), true);
        assignmentService.addSiteAssignment(staffB.getId(), site.getId(), true);
        assignmentService.addSiteAssignment(staffC.getId(), site.getId(), true);

        relationshipService.addIncompatibility(staffA.getId(), staffB.getId(), "cannot work together");

        LocalDate date = LocalDate.now().plusDays(2);
        RosterPeriod period = rosterService.createRosterPeriod(site.getId(), date, null);

        OffsetDateTime shiftStart = date.atTime(8, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime shiftEnd   = date.atTime(16, 0).atOffset(ZoneOffset.UTC);

        // 1 shift needing 2 slots: solver has a valid choice (A+C or B+C)
        rosterService.addShift(period.getId(), new ShiftCreateRequest(
                null, "Day", shiftStart, shiftEnd, 2, null));

        SolverJob job = solverService.submitSolve(period.getId(), SOLVE_LIMIT_SECONDS);
        SolverJobStatus finalStatus = awaitTerminal(job.getId());

        assertThat(finalStatus)
                .as("Should be COMPLETED: A+C or B+C are valid solutions")
                .isEqualTo(SolverJobStatus.COMPLETED);

        // Verify that the incompatible pair A and B were NOT assigned together
        List<ShiftAssignment> assignments = rosterService.getAssignments(period.getId());
        List<Long> assignedIds = assignments.stream()
                .filter(a -> a.getStaff() != null)
                .map(a -> a.getStaff().getId())
                .toList();

        boolean bothIncompatibleAssigned =
                assignedIds.contains(staffA.getId()) && assignedIds.contains(staffB.getId());
        assertThat(bothIncompatibleAssigned)
                .as("Incompatible staff A and B must not be assigned to the same shift")
                .isFalse();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SolverJobStatus awaitTerminal(Long jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            SolverJobStatus status = solverService.getSolverJob(jobId).getStatus();
            if (isTerminal(status)) return status;
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new AssertionError("Solver job " + jobId + " did not reach terminal state within "
                + (POLL_TIMEOUT_MS / 1000) + "s");
    }

    private static boolean isTerminal(SolverJobStatus status) {
        return status == SolverJobStatus.COMPLETED
                || status == SolverJobStatus.INFEASIBLE
                || status == SolverJobStatus.CANCELLED
                || status == SolverJobStatus.FAILED;
    }
}
