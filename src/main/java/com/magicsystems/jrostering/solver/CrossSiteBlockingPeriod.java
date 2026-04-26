package com.magicsystems.jrostering.solver;

import com.magicsystems.jrostering.domain.Staff;

import java.time.OffsetDateTime;

/**
 * An immutable value object representing a confirmed shift commitment held by a
 * {@link Staff} member at a different site during the current planning window.
 *
 * <p>These are injected into {@link RosterSolution} as
 * {@code @ProblemFactCollectionProperty} instances by {@code RosterSolutionMapper}.
 * The {@code RosterConstraintProvider} evaluates them using the same time-overlap
 * logic as the {@code STAFF_LEAVE_BLOCK} hard constraint, ensuring a staff member
 * cannot be double-booked across sites.</p>
 *
 * <p>Cross-site blocking is always a hard constraint, regardless of any
 * site-level rule configuration.</p>
 *
 * @param staff          the staff member who holds a commitment at another site
 * @param startDatetime  when the blocking commitment begins
 * @param endDatetime    when the blocking commitment ends
 */
public record CrossSiteBlockingPeriod(
        Staff staff,
        OffsetDateTime startDatetime,
        OffsetDateTime endDatetime
) {
}
