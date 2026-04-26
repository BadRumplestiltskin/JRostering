package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.RosterPeriodStatus;
import com.magicsystems.jrostering.domain.SolverJob;
import com.magicsystems.jrostering.domain.SolverJobStatus;
import com.magicsystems.jrostering.repository.SolverJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Recovers orphaned solver jobs left in an indeterminate state by a previous application crash
 * or unexpected restart.
 *
 * <p>This service runs once, immediately after the Spring application context is fully started
 * (via {@link ApplicationReadyEvent}), before any user interaction is possible.</p>
 *
 * <h3>Recovery procedure</h3>
 * <ol>
 *   <li>Query for all {@link SolverJob} rows in status
 *       {@link SolverJobStatus#RUNNING} or {@link SolverJobStatus#QUEUED}.</li>
 *   <li>For each orphaned job, set {@code status = FAILED} and record an error message.</li>
 *   <li>Set the corresponding {@link com.magicsystems.jrostering.domain.RosterPeriod}
 *       back to {@link RosterPeriodStatus#DRAFT} so the manager can re-submit.</li>
 *   <li>Commit all changes in a single transaction.</li>
 * </ol>
 *
 * <p>Attempting to resume an interrupted solve is intentionally not supported.
 * The {@link com.magicsystems.jrostering.domain.ShiftAssignment} state from a
 * partial run is undefined; the manager must re-submit a clean solve after recovery.</p>
 */
@Service
@Slf4j
public class StartupRecoveryService implements ApplicationListener<ApplicationReadyEvent> {

    private final SolverJobRepository solverJobRepository;

    public StartupRecoveryService(SolverJobRepository solverJobRepository) {
        this.solverJobRepository = solverJobRepository;
    }

    /**
     * Triggered once when the application context is fully ready.
     * Delegates to {@link #recoverOrphanedSolverJobs()}.
     *
     * @param event the application ready event (unused directly)
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        recoverOrphanedSolverJobs();
    }

    /**
     * Finds all orphaned solver jobs and resets them and their associated roster
     * periods to a clean state in a single transaction.
     */
    @Transactional
    public void recoverOrphanedSolverJobs() {
        List<SolverJob> orphanedJobs = solverJobRepository
                .findByStatusIn(List.of(SolverJobStatus.RUNNING, SolverJobStatus.QUEUED));

        if (orphanedJobs.isEmpty()) {
            log.debug("Startup recovery: no orphaned solver jobs found.");
            return;
        }

        log.warn("Startup recovery: found {} orphaned solver job(s) from a previous run. "
                + "Marking as FAILED and resetting roster periods to DRAFT.", orphanedJobs.size());

        OffsetDateTime now = OffsetDateTime.now();

        for (SolverJob job : orphanedJobs) {
            log.warn("Startup recovery: resetting SolverJob id={} (was {}) for RosterPeriod id={}.",
                    job.getId(), job.getStatus(), job.getRosterPeriod().getId());

            job.setStatus(SolverJobStatus.FAILED);
            job.setErrorMessage("Interrupted by application restart.");
            job.setCompletedAt(now);

            job.getRosterPeriod().setStatus(RosterPeriodStatus.DRAFT);
        }

        solverJobRepository.saveAll(orphanedJobs);

        log.warn("Startup recovery: complete. {} job(s) marked as FAILED.", orphanedJobs.size());
    }
}
