package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.RosterPeriodRepository;
import com.magicsystems.jrostering.repository.SolverJobRepository;
import com.magicsystems.jrostering.solver.RosterSolution;
import com.magicsystems.jrostering.solver.RosterSolutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Provides transactional database operations for the solver lifecycle, designed to
 * be called across a Spring proxy boundary from {@link SolverService}.
 *
 * <h3>Why a separate component?</h3>
 * <p>Spring's {@code @Transactional} relies on proxy interception. When a method on
 * a bean calls another method on the same bean object (via {@code this}), the call
 * bypasses the proxy and the annotation has no effect. {@link SolverService} delegates
 * every database write here so that each operation crosses a proxy boundary and runs
 * in its own proper transaction.</p>
 *
 * <h3>Entity loading strategy</h3>
 * <p>All public methods accept IDs rather than entity references. Each method loads
 * fresh, managed entity instances from the database at the start of its transaction.
 * This avoids any risk of writing stale state from a detached entity back to the
 * database, which could silently overwrite concurrent changes.</p>
 *
 * <h3>Visibility</h3>
 * <p>All public methods are intended solely for use by {@link SolverExecutor}.
 * They are not part of any external API.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SolverTransactionHelper {

    private final SolverJobRepository     solverJobRepository;
    private final RosterPeriodRepository  rosterPeriodRepository;
    private final RosterSolutionMapper    solutionMapper;
    private final NotificationService     notificationService;

    // =========================================================================
    // Initialisation
    // =========================================================================

    /**
     * Loads the {@link SolverJob} by ID and transitions it to
     * {@link SolverJobStatus#RUNNING}, recording the start timestamp.
     *
     * @param solverJobId the ID of the job to start
     * @throws EntityNotFoundException if no such job exists
     */
    @Transactional
    public void markJobRunning(Long solverJobId) {
        SolverJob job = requireJob(solverJobId);
        job.setStatus(SolverJobStatus.RUNNING);
        job.setStartedAt(OffsetDateTime.now());
        solverJobRepository.save(job);
    }

    /**
     * Reverts a {@link RosterPeriod} from {@link RosterPeriodStatus#SOLVING} back to
     * {@link RosterPeriodStatus#DRAFT} without touching any {@link SolverJob}.
     *
     * <p>Called by {@link SolverExecutor} when the async thread cannot proceed (e.g.
     * {@link #markJobRunning} threw before the solver was registered). Without this
     * recovery step the period would remain stranded in SOLVING status forever — the
     * background thread has already returned and no other code would reset the status.</p>
     *
     * <p>This method is intentionally lenient: if the period cannot be found it logs a
     * warning and returns rather than throwing, so that a missing-period scenario does
     * not mask the original initialisation failure.</p>
     *
     * @param rosterPeriodId the ID of the period to revert to DRAFT
     */
    @Transactional
    public void revertPeriodToDraft(Long rosterPeriodId) {
        rosterPeriodRepository.findById(rosterPeriodId).ifPresentOrElse(
                period -> {
                    period.setStatus(RosterPeriodStatus.DRAFT);
                    rosterPeriodRepository.save(period);
                    log.warn("Reverted rosterPeriodId={} from SOLVING to DRAFT "
                            + "after async initialisation failure", rosterPeriodId);
                },
                () -> log.warn("revertPeriodToDraft: rosterPeriodId={} not found — "
                        + "cannot revert status", rosterPeriodId)
        );
    }

    // =========================================================================
    // Result persistence — one method per terminal state
    // =========================================================================

    /**
     * Persists a feasible solution: saves {@link ShiftAssignment} rows,
     * transitions the period to {@link RosterPeriodStatus#SOLVED}, transitions
     * the job to {@link SolverJobStatus#COMPLETED}, and fires a completion notification.
     *
     * @param solverJobId    the ID of the solver job to finalise
     * @param rosterPeriodId the ID of the roster period to mark SOLVED
     * @param result         the feasible solution returned by the solver
     */
    @Transactional
    public void persistCompleted(Long solverJobId, Long rosterPeriodId, RosterSolution result) {
        solutionMapper.persistSolution(result);

        SolverJob    job    = requireJob(solverJobId);
        RosterPeriod period = requirePeriod(rosterPeriodId);

        job.setStatus(SolverJobStatus.COMPLETED);
        job.setFinalScore(result.getScore().toString());
        job.setCompletedAt(OffsetDateTime.now());
        solverJobRepository.save(job);

        period.setStatus(RosterPeriodStatus.SOLVED);
        rosterPeriodRepository.save(period);

        notificationService.notifySolveCompleted(job, period);
    }

    /**
     * Persists a partial (infeasible) solution: saves {@link ShiftAssignment} rows
     * including unassigned ({@code null}) slots, transitions the period to
     * {@link RosterPeriodStatus#INFEASIBLE}, transitions the job to
     * {@link SolverJobStatus#INFEASIBLE}, and fires an infeasibility notification.
     *
     * @param solverJobId    the ID of the solver job to finalise
     * @param rosterPeriodId the ID of the roster period to mark INFEASIBLE
     * @param result         the best solution found (score has negative hard or medium component)
     * @param reason         human-readable explanation of the infeasibility
     */
    @Transactional
    public void persistInfeasible(Long solverJobId, Long rosterPeriodId,
                                  RosterSolution result, String reason) {
        solutionMapper.persistSolution(result);

        SolverJob    job    = requireJob(solverJobId);
        RosterPeriod period = requirePeriod(rosterPeriodId);

        job.setStatus(SolverJobStatus.INFEASIBLE);
        job.setFinalScore(result.getScore().toString());
        job.setInfeasibleReason(reason);
        job.setCompletedAt(OffsetDateTime.now());
        solverJobRepository.save(job);

        period.setStatus(RosterPeriodStatus.INFEASIBLE);
        rosterPeriodRepository.save(period);

        notificationService.notifySolveInfeasible(job, period);
    }

    /**
     * Persists the best partial solution found before manager-initiated cancellation,
     * reverts the period to {@link RosterPeriodStatus#DRAFT}, transitions the job to
     * {@link SolverJobStatus#CANCELLED}, and fires a cancellation notification.
     *
     * @param solverJobId    the ID of the solver job to finalise
     * @param rosterPeriodId the ID of the roster period to revert to DRAFT
     * @param result         the best solution found before cancellation
     */
    @Transactional
    public void persistCancelled(Long solverJobId, Long rosterPeriodId, RosterSolution result) {
        solutionMapper.persistSolution(result);

        SolverJob    job    = requireJob(solverJobId);
        RosterPeriod period = requirePeriod(rosterPeriodId);

        job.setStatus(SolverJobStatus.CANCELLED);
        job.setFinalScore(result.getScore().toString());
        job.setCompletedAt(OffsetDateTime.now());
        solverJobRepository.save(job);

        period.setStatus(RosterPeriodStatus.DRAFT);
        rosterPeriodRepository.save(period);

        notificationService.notifySolveCancelled(job, period);
    }

    /**
     * Records a solver failure without persisting any solution. Reverts the period
     * to {@link RosterPeriodStatus#DRAFT} and transitions the job to
     * {@link SolverJobStatus#FAILED}.
     *
     * @param solverJobId    the ID of the solver job to mark FAILED
     * @param rosterPeriodId the ID of the roster period to revert to DRAFT
     * @param cause          the exception that caused the failure
     */
    @Transactional
    public void persistFailed(Long solverJobId, Long rosterPeriodId, Throwable cause) {
        SolverJob    job    = requireJob(solverJobId);
        RosterPeriod period = requirePeriod(rosterPeriodId);

        log.error("Solver failure for rosterPeriodId={} jobId={}: {}",
                rosterPeriodId, solverJobId, cause.getMessage(), cause);

        job.setStatus(SolverJobStatus.FAILED);
        job.setErrorMessage(truncate(cause.getMessage(), 1000));
        job.setCompletedAt(OffsetDateTime.now());
        solverJobRepository.save(job);

        period.setStatus(RosterPeriodStatus.DRAFT);
        rosterPeriodRepository.save(period);

        notificationService.notifySolveFailed(job, period, cause);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private SolverJob requireJob(Long solverJobId) {
        return solverJobRepository.findById(solverJobId)
                .orElseThrow(() -> EntityNotFoundException.of("SolverJob", solverJobId));
    }

    private RosterPeriod requirePeriod(Long rosterPeriodId) {
        return rosterPeriodRepository.findById(rosterPeriodId)
                .orElseThrow(() -> EntityNotFoundException.of("RosterPeriod", rosterPeriodId));
    }

    /**
     * Truncates a string to at most {@code maxLength} characters.
     * Returns an empty string if the input is {@code null}.
     */
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
