package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.SolverJob;
import com.magicsystems.jrostering.domain.SolverJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link SolverJob} entities.
 */
public interface SolverJobRepository extends JpaRepository<SolverJob, Long> {

    /**
     * Returns all solver jobs matching any of the provided statuses.
     * Used by {@code StartupRecoveryService} to find orphaned RUNNING or QUEUED jobs.
     */
    List<SolverJob> findByStatusIn(List<SolverJobStatus> statuses);

    /**
     * Returns the most recent solver job for a roster period, ordered by creation time descending.
     * A period may have multiple jobs across successive solve/cancel/re-solve cycles;
     * this method identifies the authoritative current job shown to the manager.
     *
     * @param rosterPeriodId the ID of the roster period
     * @return the most recently created job, or empty if no jobs exist for this period
     */
    Optional<SolverJob> findTopByRosterPeriodIdOrderByCreatedAtDesc(Long rosterPeriodId);
}
