package com.magicsystems.jrostering.service;

import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicsystems.jrostering.solver.RosterSolution;
import com.magicsystems.jrostering.solver.RosterSolutionMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Executes Timefold solver runs asynchronously and manages the lifecycle
 * of in-flight solver instances.
 *
 * <h3>Why a separate component from {@link SolverService}?</h3>
 * <p>Spring's {@code @Async} annotation is implemented via proxy interception.
 * When a method on a Spring bean calls another method on the <em>same</em> bean
 * (via {@code this}), the call bypasses the proxy and the annotation has no effect —
 * the method runs synchronously on the calling thread. Placing the async entry point
 * in a separate {@code @Service} ensures every call from {@link SolverService}
 * crosses a proxy boundary and is dispatched to the Spring {@code @Async} executor.</p>
 *
 * <h3>Active solver registry</h3>
 * <p>In-flight {@link Solver} instances are stored in {@link #activeSolvers}, keyed
 * by roster period ID. This allows {@link #requestCancel(Long)} to signal a running
 * solver from the web thread while the solve executes on a background thread.</p>
 *
 * <h3>Per-job time limits</h3>
 * <p>The caller passes the time limit as a parameter. A single-threaded daemon
 * {@link ScheduledExecutorService} fires {@link Solver#terminateEarly()} when the
 * limit expires.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SolverExecutor {

    private final SolverFactory<RosterSolution>                    solverFactory;
    private final SolutionManager<RosterSolution, HardMediumSoftScore> scoreManager;
    private final RosterSolutionMapper                              solutionMapper;
    private final SolverTransactionHelper                           txHelper;
    private final ObjectMapper                                      objectMapper;
    private final MeterRegistry                                     meterRegistry;

    /**
     * Active {@link Solver} instances keyed by roster period ID.
     * Written on async-thread start; removed on async-thread end.
     * Read by {@link #requestCancel(Long)} on the web thread.
     */
    private final ConcurrentHashMap<Long, Solver<RosterSolution>> activeSolvers =
            new ConcurrentHashMap<>();

    /**
     * Period IDs for which a cancel has been requested.
     * Set by {@link #requestCancel(Long)} on the web thread;
     * checked and cleared by {@link #executeSolveAsync} on the solver thread.
     */
    private final Set<Long> cancelRequested = ConcurrentHashMap.newKeySet();

    /**
     * Daemon scheduler that fires time-limit termination signals.
     * One thread is sufficient — each scheduled task is a non-blocking
     * {@link Solver#terminateEarly()} call.
     */
    private final ScheduledExecutorService terminationScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "solver-termination-scheduler");
                t.setDaemon(true);
                return t;
            });

    @PostConstruct
    void registerMetrics() {
        meterRegistry.gauge("jrostering.solver.active", activeSolvers, ConcurrentHashMap::size);
    }

    // =========================================================================
    // Public interface
    // =========================================================================

    /**
     * Requests cancellation of the solver running for the given roster period.
     *
     * <p>If no solver is currently active for that period (e.g. it just finished),
     * this call is silently ignored.</p>
     *
     * <h4>Accepted TOCTOU race</h4>
     * <p>There is an inherent time-of-check / time-of-use window between the
     * {@link #isRunning} guard in {@link SolverService#cancelSolve} and this method:
     * the solver may complete between the two calls, causing this method to find no
     * active solver and log a warning rather than signal termination. This is considered
     * an acceptable trade-off — the solve has already finished successfully, the period
     * is no longer in SOLVING status, and no harm is done. Eliminating the race would
     * require a distributed lock that adds more complexity than the edge case warrants.</p>
     *
     * @param rosterPeriodId the ID of the period whose solver should be terminated early
     */
    public void requestCancel(Long rosterPeriodId) {
        Solver<RosterSolution> solver = activeSolvers.get(rosterPeriodId);
        if (solver == null) {
            log.warn("requestCancel called for rosterPeriodId={} but no active solver found "
                    + "(solve may have just completed)", rosterPeriodId);
            return;
        }
        cancelRequested.add(rosterPeriodId);
        solver.terminateEarly();
        log.info("Cancellation signalled for rosterPeriodId={}", rosterPeriodId);
    }

    /**
     * Returns {@code true} if a solver is currently active for the given period.
     * Used by {@link SolverService#cancelSolve} to provide an accurate error message.
     */
    public boolean isRunning(Long rosterPeriodId) {
        return activeSolvers.containsKey(rosterPeriodId);
    }

    // =========================================================================
    // Async solver execution
    // =========================================================================

    /**
     * Executes the solver run on a Spring {@code @Async} background thread.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Mark the job {@link com.magicsystems.jrostering.domain.SolverJobStatus#RUNNING}
     *       via {@link SolverTransactionHelper} (own transaction).</li>
     *   <li>Load the {@link RosterSolution} via
     *       {@link RosterSolutionMapper#buildSolution(Long)} (own read-only transaction).</li>
     *   <li>Register the solver in {@link #activeSolvers}.</li>
     *   <li>Schedule time-limit termination via {@link #terminationScheduler}.</li>
     *   <li>Call {@link Solver#solve} — blocks until done for any reason.</li>
     *   <li>Check the cancel flag and score; delegate persistence to
     *       {@link SolverTransactionHelper} (own transaction per outcome).</li>
     * </ol>
     *
     * <p>This method must be called through the Spring proxy (i.e. from a different bean)
     * for {@code @Async} to take effect. Calling it via {@code this} within the same
     * class will execute it synchronously.</p>
     *
     * @param solverJobId      the ID of the persisted {@link com.magicsystems.jrostering.domain.SolverJob}
     * @param rosterPeriodId   the ID of the {@link com.magicsystems.jrostering.domain.RosterPeriod} to solve
     * @param timeLimitSeconds maximum seconds the solver may run before forced termination
     */
    @Async
    public void executeSolveAsync(Long solverJobId, Long rosterPeriodId, int timeLimitSeconds) {
        // Step 1 — mark RUNNING in its own transaction.
        // If this fails the background thread cannot proceed, so we revert the period
        // from SOLVING back to DRAFT before returning.  Without this call the period
        // would be stranded in SOLVING status forever because no other code path resets it.
        try {
            txHelper.markJobRunning(solverJobId);
        } catch (Exception e) {
            log.error("Failed to mark job RUNNING for jobId={} rosterPeriodId={}: {}",
                    solverJobId, rosterPeriodId, e.getMessage(), e);
            cancelRequested.remove(rosterPeriodId);
            txHelper.revertPeriodToDraft(rosterPeriodId);
            return;
        }

        Solver<RosterSolution> solver = solverFactory.buildSolver();
        activeSolvers.put(rosterPeriodId, solver);

        ScheduledFuture<?> timeLimitFuture = terminationScheduler.schedule(
                solver::terminateEarly,
                timeLimitSeconds,
                TimeUnit.SECONDS
        );

        RosterSolution result = null;
        Timer.Sample timerSample = Timer.start(meterRegistry);

        try {
            // Step 2 — load problem in a fresh read-only transaction
            RosterSolution problem = solutionMapper.buildSolution(rosterPeriodId);

            // Step 3 — solve (blocks until termination for any reason)
            result = solver.solve(problem);
            timeLimitFuture.cancel(false);

        } catch (Exception e) {
            timeLimitFuture.cancel(false);
            activeSolvers.remove(rosterPeriodId);
            cancelRequested.remove(rosterPeriodId);
            timerSample.stop(meterRegistry.timer("jrostering.solver.solve", "outcome", "failed"));
            txHelper.persistFailed(solverJobId, rosterPeriodId, e);
            return;

        } finally {
            // Guard: always remove even if an unexpected Throwable escapes the catch.
            activeSolvers.remove(rosterPeriodId);
        }

        // Step 4 — persist result
        boolean wasCancelled = cancelRequested.remove(rosterPeriodId);
        String violationJson = buildViolationJson(result);

        if (wasCancelled) {
            timerSample.stop(meterRegistry.timer("jrostering.solver.solve", "outcome", "cancelled"));
            txHelper.persistCancelled(solverJobId, rosterPeriodId, result, violationJson);
        } else {
            HardMediumSoftScore score = result.getScore();
            boolean feasible = score.hardScore() == 0 && score.mediumScore() == 0;
            timerSample.stop(meterRegistry.timer("jrostering.solver.solve",
                    "outcome", feasible ? "completed" : "infeasible"));
            persistFinalResult(solverJobId, rosterPeriodId, result, violationJson);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Determines whether the result is feasible (hard == 0 AND medium == 0)
     * and delegates to the appropriate {@link SolverTransactionHelper} method.
     */
    private void persistFinalResult(Long solverJobId, Long rosterPeriodId,
                                    RosterSolution result, String violationJson) {
        HardMediumSoftScore score = result.getScore();
        boolean feasible = score.hardScore() == 0 && score.mediumScore() == 0;

        if (feasible) {
            txHelper.persistCompleted(solverJobId, rosterPeriodId, result, violationJson);
        } else {
            String reason = buildInfeasibleReason(score);
            txHelper.persistInfeasible(solverJobId, rosterPeriodId, result, reason, violationJson);
        }
    }

    /**
     * Extracts per-constraint violation totals from the solved solution using
     * Timefold's {@link ScoreManager} and serialises them as a JSON array.
     *
     * <p>Only constraints with at least one violation (non-zero score contribution)
     * are included.  Failures are caught so that a scoring error never prevents
     * the result from being persisted.</p>
     */
    private String buildViolationJson(RosterSolution result) {
        try {
            var explanation = scoreManager.explain(result);
            var entries = explanation.getConstraintMatchTotalMap().values().stream()
                    .filter(total -> {
                        HardMediumSoftScore s = total.getScore();
                        return s.hardScore() != 0 || s.mediumScore() != 0 || s.softScore() != 0;
                    })
                    .map(total -> new ViolationEntry(
                            total.getConstraintRef().constraintName(),
                            total.getScore().toString(),
                            total.getConstraintMatchSet().size()
                    ))
                    .toList();
            return objectMapper.writeValueAsString(entries);
        } catch (Exception e) {
            log.warn("Could not extract constraint violation detail — report will be empty: {}",
                    e.getMessage());
            return "[]";
        }
    }

    /**
     * Builds a human-readable infeasibility summary from the score.
     */
    private static String buildInfeasibleReason(HardMediumSoftScore score) {
        StringBuilder sb = new StringBuilder("Infeasible solution:");
        if (score.hardScore() < 0) {
            sb.append(" ").append(Math.abs(score.hardScore()))
              .append(" hard constraint violation(s)");
        }
        if (score.mediumScore() < 0) {
            if (score.hardScore() < 0) {
                sb.append(",");
            }
            sb.append(" ").append(Math.abs(score.mediumScore()))
              .append(" medium constraint violation(s)");
        }
        sb.append(". Score: ").append(score);
        return sb.toString();
    }

    private record ViolationEntry(String constraintName, String score, int violations) {}
}
