package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.domain.SolverJob;
import com.magicsystems.jrostering.service.SolverService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for the solver lifecycle.
 *
 * <p>All endpoints are under {@code /api/solver} and require HTTP Basic authentication.
 * The caller supplies the time limit in seconds (1–86 400). The solve runs asynchronously;
 * poll {@code GET /api/solver/jobs/{jobId}} for completion status.</p>
 */
@RestController
@RequestMapping("/api/solver")
@RequiredArgsConstructor
@Validated
public class SolverController {

    private final SolverService solverService;

    /**
     * Submits a solve job for the given roster period.
     *
     * @param rosterPeriodId   the period to solve; must be in DRAFT or INFEASIBLE status
     * @param timeLimitSeconds wall-clock time limit (1–86 400 seconds)
     * @return 200 with the created {@link SolverJob} in QUEUED status
     */
    @PostMapping("/{rosterPeriodId}/submit")
    public ResponseEntity<SolverJob> submit(
            @PathVariable Long rosterPeriodId,
            @RequestParam @Min(1) @Max(86_400) int timeLimitSeconds) {

        SolverJob job = solverService.submitSolve(rosterPeriodId, timeLimitSeconds);
        return ResponseEntity.ok(job);
    }

    /**
     * Requests early cancellation of the active solver for the given roster period.
     *
     * @param rosterPeriodId the period whose solve should be terminated
     * @return 204 No Content on success
     */
    @DeleteMapping("/{rosterPeriodId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long rosterPeriodId) {
        solverService.cancelSolve(rosterPeriodId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the current state of a solver job.
     *
     * @param solverJobId the solver job to look up
     * @return 200 with the {@link SolverJob}, or 404 if not found
     */
    @GetMapping("/jobs/{solverJobId}")
    public ResponseEntity<SolverJob> getJob(@PathVariable Long solverJobId) {
        return ResponseEntity.ok(solverService.getSolverJob(solverJobId));
    }
}
