package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.RosterPeriod;
import com.magicsystems.jrostering.domain.Shift;
import com.magicsystems.jrostering.domain.ShiftAssignment;
import com.magicsystems.jrostering.domain.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Repository for {@link ShiftAssignment} entities.
 */
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    /** Returns all assignments for the given shift. */
    List<ShiftAssignment> findByShift(Shift shift);

    /**
     * Returns confirmed (non-null staff) assignments for a given staff member on shifts
     * that overlap the specified time window and belong to a different site than the one
     * currently being solved. Used by {@code RosterSolutionMapper} to build
     * {@code CrossSiteBlockingPeriod} problem facts.
     *
     * @deprecated Use {@link #findCrossSiteAssignmentsByStaffIn} to avoid N+1 queries
     *             when building the solution for a full site. This single-staff variant
     *             is retained for targeted lookups only.
     */
    @Deprecated(since = "0.2")
    @Query("""
            SELECT sa FROM ShiftAssignment sa
              JOIN sa.shift s
              JOIN s.rosterPeriod rp
             WHERE sa.staff = :staff
               AND rp.site.id <> :excludeSiteId
               AND rp.status IN ('PUBLISHED', 'SOLVED')
               AND s.startDatetime < :windowEnd
               AND s.endDatetime   > :windowStart
            """)
    List<ShiftAssignment> findCrossSiteAssignments(
            @Param("staff")         Staff staff,
            @Param("excludeSiteId") Long excludeSiteId,
            @Param("windowStart")   OffsetDateTime windowStart,
            @Param("windowEnd")     OffsetDateTime windowEnd
    );

    /**
     * Returns all confirmed cross-site assignments for a batch of staff members whose
     * shifts overlap the specified time window and belong to a different site.
     *
     * <p>This is the batch equivalent of {@link #findCrossSiteAssignments} and replaces
     * the per-staff N+1 loop in {@code RosterSolutionMapper.buildCrossSiteBlockingPeriods}.
     * A single query is issued regardless of how many staff members are in the set.</p>
     *
     * @param staffList     the set of staff members to check for cross-site commitments
     * @param excludeSiteId the ID of the site currently being solved (excluded from results)
     * @param windowStart   start of the planning window
     * @param windowEnd     exclusive end of the planning window
     * @return assignments at other PUBLISHED or SOLVED sites that overlap the window
     */
    @Query("""
            SELECT sa FROM ShiftAssignment sa
              JOIN sa.shift s
              JOIN s.rosterPeriod rp
             WHERE sa.staff IN :staffList
               AND rp.site.id <> :excludeSiteId
               AND rp.status IN ('PUBLISHED', 'SOLVED')
               AND s.startDatetime < :windowEnd
               AND s.endDatetime   > :windowStart
            """)
    List<ShiftAssignment> findCrossSiteAssignmentsByStaffIn(
            @Param("staffList")     Collection<Staff> staffList,
            @Param("excludeSiteId") Long excludeSiteId,
            @Param("windowStart")   OffsetDateTime windowStart,
            @Param("windowEnd")     OffsetDateTime windowEnd
    );

    /** Returns all assignments in a roster period, including unassigned slots. */
    @Query("""
            SELECT sa FROM ShiftAssignment sa
              JOIN sa.shift s
             WHERE s.rosterPeriod = :rosterPeriod
            """)
    List<ShiftAssignment> findByRosterPeriod(@Param("rosterPeriod") RosterPeriod rosterPeriod);
}
