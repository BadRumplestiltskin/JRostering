package com.magicsystems.jrostering.integration;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.OrganisationRepository;
import com.magicsystems.jrostering.service.RosterService;
import com.magicsystems.jrostering.service.RosterService.ShiftCreateRequest;
import com.magicsystems.jrostering.service.SiteService;
import com.magicsystems.jrostering.service.SiteService.SiteCreateRequest;
import com.magicsystems.jrostering.service.SolverService;
import com.magicsystems.jrostering.service.StaffAssignmentService;
import com.magicsystems.jrostering.service.StaffService;
import com.magicsystems.jrostering.service.StaffService.StaffCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying that the full solver pipeline —
 * data seeding → solve submission → async execution → result persistence —
 * completes without errors on a real PostgreSQL database.
 *
 * <p>Run explicitly with: {@code mvn test -Dgroups=integration}</p>
 *
 * <p>This test does NOT use {@code @Transactional} because the solver
 * executes on a background thread and needs to see committed data. The
 * database is owned by a Testcontainers container that is discarded after
 * the test run.</p>
 */
class SolverE2EIntegrationTest extends IntegrationTestBase {

    private static final int  SOLVE_LIMIT_SECONDS = 5;
    private static final long POLL_INTERVAL_MS    = 500;
    private static final long POLL_TIMEOUT_MS     = 30_000;

    @Autowired OrganisationRepository organisationRepository;
    @Autowired SiteService            siteService;
    @Autowired StaffService           staffService;
    @Autowired StaffAssignmentService assignmentService;
    @Autowired RosterService          rosterService;
    @Autowired SolverService          solverService;

    @Test
    void solver_assignsSingleSlot_andReachesTerminalState() throws InterruptedException {
        Organisation org = organisationRepository.findAll().getFirst();

        // ── Seed ──────────────────────────────────────────────────────────────
        Site site = siteService.create(org.getId(),
                new SiteCreateRequest("E2E Solver Site", "UTC", null));

        Staff staff = staffService.create(org.getId(), new StaffCreateRequest(
                "E2E", "Solver", "e2e-solver@test.invalid", null,
                EmploymentType.FULL_TIME, null, null));

        assignmentService.addSiteAssignment(staff.getId(), site.getId(), true);

        LocalDate startDate = LocalDate.now().plusDays(1);
        RosterPeriod period = rosterService.createRosterPeriod(site.getId(), startDate, null);

        OffsetDateTime shiftStart = startDate.atTime(8, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime shiftEnd   = startDate.atTime(16, 0).atOffset(ZoneOffset.UTC);

        rosterService.addShift(period.getId(), new ShiftCreateRequest(
                null, "Day", shiftStart, shiftEnd, 1, null));

        // ── Submit ────────────────────────────────────────────────────────────
        SolverJob job = solverService.submitSolve(period.getId(), SOLVE_LIMIT_SECONDS);
        assertThat(job.getStatus()).isEqualTo(SolverJobStatus.QUEUED);

        // ── Poll until terminal ───────────────────────────────────────────────
        SolverJobStatus finalStatus = awaitTerminal(job.getId());

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(finalStatus)
                .as("Solver should finish COMPLETED or INFEASIBLE, not FAILED")
                .isIn(SolverJobStatus.COMPLETED, SolverJobStatus.INFEASIBLE,
                        SolverJobStatus.CANCELLED);

        // With exactly 1 staff for 1 slot, a feasible solution must exist.
        assertThat(finalStatus)
                .as("1 staff for 1 slot must produce a feasible (COMPLETED) solution")
                .isEqualTo(SolverJobStatus.COMPLETED);

        // Verify the slot was actually assigned.
        var assignments = rosterService.getAssignments(period.getId());
        assertThat(assignments)
                .as("The single slot should have been assigned")
                .anyMatch(a -> a.getStaff() != null);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SolverJobStatus awaitTerminal(Long jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            SolverJobStatus status = solverService.getSolverJob(jobId).getStatus();
            if (isTerminal(status)) {
                return status;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new AssertionError("Solver job " + jobId + " did not reach a terminal state within "
                + (POLL_TIMEOUT_MS / 1000) + " seconds");
    }

    private static boolean isTerminal(SolverJobStatus status) {
        return status == SolverJobStatus.COMPLETED
                || status == SolverJobStatus.INFEASIBLE
                || status == SolverJobStatus.CANCELLED
                || status == SolverJobStatus.FAILED;
    }
}
