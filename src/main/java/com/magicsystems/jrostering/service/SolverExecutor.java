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

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * <h3>Executor choice</h3>
 * <p>The {@code @Async("solverExecutor")} qualifier routes this method to the
 * <em>platform-thread</em> pool defined in {@code AsyncConfig}. Timefold's internal
 * search is CPU-bound and runs continuously without yielding; a virtual thread would
 * pin a carrier thread for the entire solve, starving other virtual threads of
 * scheduler capacity.</p>
 *
 * <h3>Time-limit signalling</h3>
 * <p>Each solve spawns a single virtual thread that sleeps for {@code timeLimitSeconds}
 * then calls {@link Solver#terminateEarly()}. The virtual thread is interrupted
 * (and exits immediately) when the solve finishes before the limit. This replaces the
 * shared {@code ScheduledExecutorService} from earlier versions, eliminating the
 * need for a {@code @PreDestroy} shutdown hook.</p>
 *
 * <h3>Active solver registry</h3>
 * <p>In-flight {@link Solver} instances are stored in {@link #activeSolvers}, keyed
 * by roster period ID. This allows {@link #requestCancel(Long)} to signal a running
 * solver from the web thread while the solve executes on a background thread.</p>
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
     * is no longer in SOLVING status, and no harm is done.</p>
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
     * Executes the solver run on the dedicated platform-thread {@code solverExecutor} pool.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Mark the job {@link com.magicsystems.jrostering.domain.SolverJobStatus#RUNNING}
     *       via {@link SolverTransactionHelper} (own transaction).</li>
     *   <li>Load the {@link RosterSolution} (own read-only transaction).</li>
     *   <li>Register the solver in {@link #activeSolvers}.</li>
     *   <li>Spawn a virtual timeout thread that calls {@link Solver#terminateEarly()}
     *       after {@code timeLimitSeconds}. The thread is interrupted when the solve
     *       finishes first, so it exits immediately without ever calling
     *       {@code terminateEarly}.</li>
     *   <li>Call {@link Solver#solve} — blocks on the platform thread until done.</li>
     *   <li>Check the cancel flag and score; delegate persistence to
     *       {@link SolverTransactionHelper} (own transaction per outcome).</li>
     * </ol>
     *
     * @param solverJobId      the ID of the persisted {@link com.magicsystems.jrostering.domain.SolverJob}
     * @param rosterPeriodId   the ID of the {@link com.magicsystems.jrostering.domain.RosterPeriod} to solve
     * @param timeLimitSeconds maximum seconds the solver may run before forced termination
     */
    @Async("solverExecutor")
    public void executeSolveAsync(Long solverJobId, Long rosterPeriodId, int timeLimitSeconds) {
        // Step 1 — mark RUNNING in its own transaction.
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

        // Spawn a virtual thread for the time-limit signal. It sleeps for the limit
        // then calls terminateEarly(). If the solve finishes first, we interrupt it
        // so it exits without ever signalling termination.
        Thread timeoutThread = Thread.ofVirtual()
                .name("solver-timeout-" + rosterPeriodId)
                .start(() -> {
                    try {
                        Thread.sleep(Duration.ofSeconds(timeLimitSeconds));
                        solver.terminateEarly();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

        RosterSolution result = null;
        Timer.Sample timerSample = Timer.start(meterRegistry);

        try {
            // Step 2 — load problem in a fresh read-only transaction
            RosterSolution problem = solutionMapper.buildSolution(rosterPeriodId);

            // Step 3 — solve (blocks until termination for any reason)
            result = solver.solve(problem);

        } catch (Exception e) {
            timeoutThread.interrupt();
            activeSolvers.remove(rosterPeriodId);
            cancelRequested.remove(rosterPeriodId);
            timerSample.stop(meterRegistry.timer("jrostering.solver.solve", "outcome", "failed"));
            txHelper.persistFailed(solverJobId, rosterPeriodId, e);
            return;

        } finally {
            // Guard: always remove even if an unexpected Throwable escapes the catch.
            activeSolvers.remove(rosterPeriodId);
        }

        // Solve finished — stop the timeout thread before inspecting results.
        timeoutThread.interrupt();

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
