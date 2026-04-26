package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.RosterPeriodRepository;
import com.magicsystems.jrostering.repository.SolverJobRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Orchestrates the submission and cancellation of Timefold solver runs for
 * {@link RosterPeriod} instances.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Validating that a period is in the correct state before a solve starts or
 *       is cancelled.</li>
 *   <li>Creating the {@link SolverJob} record and transitioning the period to
 *       {@link RosterPeriodStatus#SOLVING} atomically within a transaction.</li>
 *   <li>Delegating async execution to {@link SolverExecutor}, which holds the
 *       {@code @Async} entry point.</li>
 *   <li>Routing cancellation requests to {@link SolverExecutor#requestCancel(Long)}.</li>
 * </ul>
 *
 * <h3>Threading note</h3>
 * <p>{@link #submitSolve} calls {@link SolverExecutor#executeSolveAsync} through
 * the injected {@link SolverExecutor} proxy (not via {@code this}), so the
 * {@code @Async} annotation on that method fires correctly and the solve runs on
 * a background thread. The {@code @Transactional} here commits before the async
 * thread starts, ensuring the persisted {@link SolverJob} row is visible to the
 * background thread when it performs its first database read.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Validated
@Slf4j
public class SolverService {

    private final SolverExecutor         solverExecutor;
    private final RosterPeriodRepository rosterPeriodRepository;
    private final SolverJobRepository    solverJobRepository;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Creates a solver job, transitions the roster period to
     * {@link RosterPeriodStatus#SOLVING}, and dispatches an asynchronous solve.
     *
     * <p>The returned {@link SolverJob} is in {@link SolverJobStatus#QUEUED} status.
     * It transitions to {@link SolverJobStatus#RUNNING} once the background thread
     * picks it up. This method always returns before the solve begins.</p>
     *
     * @param rosterPeriodId   the ID of the period to solve
     * @param timeLimitSeconds maximum wall-clock seconds the solver may run; must be between
     *                         1 and 86 400 (24 hours)
     * @return the persisted {@link SolverJob} in QUEUED status
     * @throws EntityNotFoundException   if the period does not exist
     * @throws InvalidOperationException if the period is not in DRAFT or INFEASIBLE status
     */
    @Transactional
    public SolverJob submitSolve(Long rosterPeriodId,
                                 @Min(1) @Max(86_400) int timeLimitSeconds) {
        RosterPeriod period = requirePeriod(rosterPeriodId);

        if (period.getStatus() != RosterPeriodStatus.DRAFT
                && period.getStatus() != RosterPeriodStatus.INFEASIBLE) {
            throw new InvalidOperationException(
                    "Cannot submit a solve for roster period " + rosterPeriodId
                    + " — current status is " + period.getStatus()
                    + ". Only DRAFT or INFEASIBLE periods may be solved.");
        }

        SolverJob job = new SolverJob();
        job.setRosterPeriod(period);
        job.setStatus(SolverJobStatus.QUEUED);
        job.setTimeLimitSeconds(timeLimitSeconds);
        SolverJob savedJob = solverJobRepository.save(job);

        period.setStatus(RosterPeriodStatus.SOLVING);
        rosterPeriodRepository.save(period);

        log.info("Solve submitted — rosterPeriodId={} jobId={} timeLimitSeconds={}",
                rosterPeriodId, savedJob.getId(), timeLimitSeconds);

        // Dispatch to the SolverExecutor proxy so @Async fires on the background thread.
        // This transaction commits before the async thread starts — the persisted job row
        // is therefore visible when the background thread reads it.
        solverExecutor.executeSolveAsync(savedJob.getId(), rosterPeriodId, timeLimitSeconds);

        return savedJob;
    }

    /**
     * Requests early termination of the active solver for the given roster period.
     *
     * <p>Delegates the actual signal to {@link SolverExecutor#requestCancel(Long)},
     * which calls {@link ai.timefold.solver.core.api.solver.Solver#terminateEarly()}
     * on the running solver instance. The background thread then persists the best
     * partial solution and marks the job {@link SolverJobStatus#CANCELLED}.</p>
     *
     * @param rosterPeriodId the ID of the period whose solve should be cancelled
     * @throws EntityNotFoundException   if the period does not exist
     * @throws InvalidOperationException if the period is not in SOLVING status
     */
    @Transactional(readOnly = true)
    public void cancelSolve(Long rosterPeriodId) {
        RosterPeriod period = requirePeriod(rosterPeriodId);

        if (period.getStatus() != RosterPeriodStatus.SOLVING) {
            throw new InvalidOperationException(
                    "Cannot cancel roster period " + rosterPeriodId
                    + " — current status is " + period.getStatus()
                    + ". Only SOLVING periods can be cancelled.");
        }

        solverExecutor.requestCancel(rosterPeriodId);
    }

    /**
     * Retrieves a solver job by its ID.
     *
     * @param solverJobId the ID of the job to retrieve
     * @return the {@link SolverJob}
     * @throws EntityNotFoundException if no such job exists
     */
    public SolverJob getSolverJob(Long solverJobId) {
        return solverJobRepository.findById(solverJobId)
                .orElseThrow(() -> EntityNotFoundException.of("SolverJob", solverJobId));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private RosterPeriod requirePeriod(Long rosterPeriodId) {
        return rosterPeriodRepository.findById(rosterPeriodId)
                .orElseThrow(() -> EntityNotFoundException.of("RosterPeriod", rosterPeriodId));
    }
}
