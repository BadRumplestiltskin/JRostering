package com.magicsystems.jrostering.repository;

import com.magicsystems.jrostering.domain.Leave;
import com.magicsystems.jrostering.domain.LeaveStatus;
import com.magicsystems.jrostering.domain.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Repository for {@link Leave} entities.
 */
public interface LeaveRepository extends JpaRepository<Leave, Long> {

    /**
     * Returns all leave records for the given staff member, ordered by start date.
     * Used by {@code StaffService} to list a staff member's full leave history.
     */
    List<Leave> findByStaffOrderByStartDateAsc(Staff staff);

    /**
     * Returns all leave records for a given staff member that overlap the specified
     * date range and match the given status. Used by the solver mapper to load
     * relevant leave as problem facts.
     */
    @Query("""
            SELECT l FROM Leave l
             WHERE l.staff = :staff
               AND l.status = :status
               AND l.startDate <= :periodEnd
               AND l.endDate   >= :periodStart
            """)
    List<Leave> findByStaffAndStatusAndDateRange(
            @Param("staff")       Staff staff,
            @Param("status")      LeaveStatus status,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd")   LocalDate periodEnd
    );

    /**
     * Returns all non-REJECTED leave records for the given staff member that overlap the
     * specified date range, excluding a specific leave ID (pass {@code null} to exclude nothing).
     *
     * <p>Used by {@code StaffService} to detect overlapping leave before saving:</p>
     * <ul>
     *   <li>On {@code addLeave}: checks REQUESTED or APPROVED overlap (excludeId = null).</li>
     *   <li>On {@code updateLeaveStatus} to APPROVED: checks existing APPROVED overlap
     *       (excludeId = the leave being approved, statuses = [APPROVED]).</li>
     * </ul>
     */
    @Query("""
            SELECT l FROM Leave l
             WHERE l.staff = :staff
               AND l.status IN :statuses
               AND l.startDate <= :endDate
               AND l.endDate   >= :startDate
               AND (:excludeId IS NULL OR l.id <> :excludeId)
            """)
    List<Leave> findOverlapping(
            @Param("staff")     Staff staff,
            @Param("statuses")  Set<LeaveStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate,
            @Param("excludeId") Long excludeId
    );

    /**
     * Returns all leave records (APPROVED and REQUESTED) for a batch of staff members
     * that overlap the specified date window. Used by {@code RosterSolutionMapper} to
     * batch-load leave for the entire eligible staff pool in a single query.
     *
     * <p>Both {@link LeaveStatus#APPROVED} and {@link LeaveStatus#REQUESTED} records are
     * returned so the constraint provider can distinguish hard block vs soft preference.</p>
     */
    @Query("""
            SELECT l FROM Leave l
             WHERE l.staff IN :staffList
               AND l.status IN (:approvedStatus, :requestedStatus)
               AND l.startDate <= :periodEnd
               AND l.endDate   >= :periodStart
            """)
    List<Leave> findByStaffInAndStatusInAndDateRange(
            @Param("staffList")        Collection<Staff> staffList,
            @Param("approvedStatus")   LeaveStatus approvedStatus,
            @Param("requestedStatus")  LeaveStatus requestedStatus,
            @Param("periodStart")      LocalDate periodStart,
            @Param("periodEnd")        LocalDate periodEnd
    );
}
