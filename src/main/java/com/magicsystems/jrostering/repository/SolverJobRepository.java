package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.SolverJob;
import com.magicsystems.jrostering.domain.SolverJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link SolverJob} entities.
 */
public interface SolverJobRepository extends JpaRepository<SolverJob, Long> {

    /**
     * Returns all solver jobs matching any of the provided statuses.
     * Used by {@code StartupRecoveryService} to find orphaned RUNNING or QUEUED jobs.
     */
    List<SolverJob> findByStatusIn(List<SolverJobStatus> statuses);
}
