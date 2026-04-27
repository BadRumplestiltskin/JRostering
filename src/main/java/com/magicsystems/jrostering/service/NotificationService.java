package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.RosterPeriod;
import com.magicsystems.jrostering.domain.SolverJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Dispatches notifications for solver lifecycle events.
 *
 * <h3>Current implementation</h3>
 * <p>All events are written to the application log at an appropriate severity level.
 * This is sufficient for Phase 1 (API-only) operation and integration testing.</p>
 *
 * <h3>Future: Vaadin Push</h3>
 * <p>When the Vaadin UI layer is built, this service will also broadcast events to
 * connected browser clients via Vaadin Server Push. The broadcast will be injected
 * here so that callers (principally {@code SolverService}) remain unaware of the
 * transport mechanism. No changes to call sites will be required at that point —
 * only this class needs to be updated.</p>
 *
 * <h3>Thread safety</h3>
 * <p>All public methods are called from the background solver thread managed by the
 * Spring {@code @Async} executor. Implementations must be thread-safe. The current
 * log-only implementation is inherently thread-safe.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final ApplicationEventPublisher eventPublisher;

    // =========================================================================
    // Solver lifecycle events
    // =========================================================================

    /**
     * Called when a solver run finishes with a fully feasible solution
     * (zero negative hard constraints, zero negative medium constraints).
     *
     * @param job    the completed {@link SolverJob}; {@code status} is already
     *               {@code COMPLETED} and {@code finalScore} is populated
     * @param period the {@link RosterPeriod} that was solved; {@code status} is
     *               already {@code SOLVED}
     */
    public void notifySolveCompleted(SolverJob job, RosterPeriod period) {
        log.info(
                "Solve COMPLETED — rosterPeriodId={} siteId={} jobId={} score='{}' elapsed={}s",
                period.getId(),
                period.getSite().getId(),
                job.getId(),
                job.getFinalScore(),
                elapsedSeconds(job)
        );
        eventPublisher.publishEvent(new SolverLifecycleEvent.Completed(job, period));
    }

    /**
     * Called when a solver run terminates but the best solution found still
     * violates one or more hard or medium constraints (INFEASIBLE result).
     *
     * <p>The partial solution is preserved in {@link com.magicsystems.jrostering.domain.ShiftAssignment}
     * rows; unresolvable slots remain unassigned ({@code staff = null}).</p>
     *
     * @param job    the completed {@link SolverJob}; {@code status} is
     *               {@code INFEASIBLE}, {@code finalScore} and
     *               {@code infeasibleReason} are populated
     * @param period the {@link RosterPeriod}; {@code status} is {@code INFEASIBLE}
     */
    public void notifySolveInfeasible(SolverJob job, RosterPeriod period) {
        log.warn(
                "Solve INFEASIBLE — rosterPeriodId={} siteId={} jobId={} score='{}' reason='{}' elapsed={}s",
                period.getId(),
                period.getSite().getId(),
                job.getId(),
                job.getFinalScore(),
                job.getInfeasibleReason(),
                elapsedSeconds(job)
        );
        eventPublisher.publishEvent(new SolverLifecycleEvent.Infeasible(job, period));
    }

    /**
     * Called when a solver run is cancelled by a manager before the time limit
     * expires. The best solution found up to the cancellation point is preserved.
     *
     * @param job    the cancelled {@link SolverJob}; {@code status} is
     *               {@code CANCELLED} and {@code finalScore} reflects the best
     *               partial solution
     * @param period the {@link RosterPeriod}; {@code status} is reset to
     *               {@code DRAFT}
     */
    public void notifySolveCancelled(SolverJob job, RosterPeriod period) {
        log.info(
                "Solve CANCELLED — rosterPeriodId={} siteId={} jobId={} bestScore='{}' elapsed={}s",
                period.getId(),
                period.getSite().getId(),
                job.getId(),
                job.getFinalScore(),
                elapsedSeconds(job)
        );
        eventPublisher.publishEvent(new SolverLifecycleEvent.Cancelled(job, period));
    }

    /**
     * Called when a solver run terminates due to an unexpected exception.
     *
     * @param job       the failed {@link SolverJob}; {@code status} is
     *                  {@code FAILED} and {@code errorMessage} is populated
     * @param period    the {@link RosterPeriod}; {@code status} is reset to
     *                  {@code DRAFT}
     * @param throwable the underlying exception that caused the failure
     */
    public void notifySolveFailed(SolverJob job, RosterPeriod period, Throwable throwable) {
        log.error(
                "Solve FAILED — rosterPeriodId={} siteId={} jobId={} elapsed={}s — {}",
                period.getId(),
                period.getSite().getId(),
                job.getId(),
                elapsedSeconds(job),
                throwable.getMessage(),
                throwable
        );
        eventPublisher.publishEvent(new SolverLifecycleEvent.Failed(job, period, throwable));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Computes elapsed wall-clock time for a job in whole seconds.
     * Returns {@code -1} if {@code startedAt} was never set (e.g. the job
     * failed before the solver thread began executing).
     */
    private long elapsedSeconds(SolverJob job) {
        if (job.getStartedAt() == null || job.getCompletedAt() == null) {
            return -1L;
        }
        return java.time.Duration.between(job.getStartedAt(), job.getCompletedAt()).toSeconds();
    }
}
