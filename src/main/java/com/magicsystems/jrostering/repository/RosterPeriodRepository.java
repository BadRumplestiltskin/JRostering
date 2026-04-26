package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.RosterPeriod;
import com.magicsystems.jrostering.domain.RosterPeriodStatus;
import com.magicsystems.jrostering.domain.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link RosterPeriod} entities.
 */
public interface RosterPeriodRepository extends JpaRepository<RosterPeriod, Long> {

    /** Returns all roster periods for the given site, ordered chronologically. */
    List<RosterPeriod> findBySiteOrderByStartDateAsc(Site site);

    /** Returns all roster periods matching the given status across all sites. */
    List<RosterPeriod> findByStatus(RosterPeriodStatus status);

    /**
     * Returns the period that directly follows the given period in a sequential chain,
     * i.e. the period whose {@code previousPeriod} FK points to the given period.
     * Used by {@code RosterService} when cascading a period-1 revert to period 2.
     */
    Optional<RosterPeriod> findByPreviousPeriod(RosterPeriod previousPeriod);
}
