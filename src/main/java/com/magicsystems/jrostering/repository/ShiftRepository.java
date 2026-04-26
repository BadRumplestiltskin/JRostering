package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.RosterPeriod;
import com.magicsystems.jrostering.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link Shift} entities.
 */
public interface ShiftRepository extends JpaRepository<Shift, Long> {

    /** Returns all shifts within a given roster period, ordered by start time. */
    List<Shift> findByRosterPeriodOrderByStartDatetimeAsc(RosterPeriod rosterPeriod);
}
