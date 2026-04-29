package com.magicsystems.jrostering.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.magicsystems.jrostering.domain.*;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Timefold constraint provider for the JRostering solver.
 *
 * <h3>Score tiers</h3>
 * <ul>
 *   <li><b>Hard</b> — infeasibility; solution is rejected.</li>
 *   <li><b>Medium</b> — near-absolute; violated only when no feasible solution exists
 *       (used by STAFF_AVAILABILITY_BLOCK when configured to MEDIUM level).</li>
 *   <li><b>Soft</b> — weighted preference optimisation; solver minimises violations.</li>
 * </ul>
 *
 * <h3>Constraint activation</h3>
 * <p>Each rule type maps to one {@link RuleConfiguration} row in the solution.
 * A constraint only fires when the corresponding {@code RuleConfiguration.enabled == true}
 * and {@code constraintLevel} matches the constraint variant (HARD/MEDIUM/SOFT).
 * This means every rule has three registered constraint methods; only the one
 * matching the site's configuration ever produces a non-zero penalty.</p>
 *
 * <h3>Cross-site blocking</h3>
 * <p>Cross-site assignments are enforced via {@link CrossSiteBlockingPeriod} problem facts,
 * always as a hard constraint regardless of per-site rule configuration.</p>
 *
 * <h3>Consecutive-days detection</h3>
 * <p>The consecutive-days constraints deduplicate assignments to (staff, date) pairs via
 * {@code groupBy}, then count distinct worked dates in the parameterised look-back window.
 * This is exact for rosters where staff work at most one shift per day.  For multi-shift-per-day
 * rosters the deduplication step already collapses those to a single worked-date entry,
 * so the count remains correct.</p>
 *
 * <h3>Fairness distribution constraints</h3>
 * <p>Fairness penalties (hours, weekends, night shifts) are computed by collecting all
 * per-staff totals into a single {@code Map} tuple and evaluating the max-minus-min
 * spread in one pass.  This yields a single match per solve step and is intentionally
 * coarse-grained — it guides the solver toward equitable distribution rather than
 * enforcing exact equality.</p>
 *
 * <h3>Fan-out helpers</h3>
 * <p>Each configurable rule has three constraint variants (HARD/MEDIUM/SOFT) sharing the
 * same logic body; only the score tier differs.  The private {@link #forAllLevels} helper
 * collapses each fan-out method to a one-liner while keeping individual constraint names
 * fully qualified (e.g. {@code MIN_REST_BETWEEN_SHIFTS_HARD}) for Timefold's score
 * explanation output.  Rules with two sub-variants (STAFF_MUST_PAIR, PREFERRED_SHIFT_TYPE)
 * are expanded explicitly in their fan-out method.</p>
 */
public class RosterConstraintProvider implements ConstraintProvider {

    // =========================================================================
    // Constraint registration
    // =========================================================================

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        List<Constraint> all = new ArrayList<>();

        // Cross-site (always hard — no RuleConfiguration gate)
        all.add(crossSiteBlocking(factory));

        // Hard rules — each rule registers HARD / MEDIUM / SOFT variants
        addAll(all, minRestBetweenShiftsConstraints(factory));
        addAll(all, maxHoursPerDayConstraints(factory));
        addAll(all, maxHoursPerWeekConstraints(factory));
        addAll(all, maxConsecutiveDaysConstraints(factory));
        addAll(all, minStaffPerShiftConstraints(factory));
        addAll(all, minQualifiedStaffPerShiftConstraints(factory));
        addAll(all, qualificationRequiredForShiftConstraints(factory));
        addAll(all, staffMutualExclusionConstraints(factory));
        addAll(all, staffMustPairConstraints(factory));
        addAll(all, staffLeaveBlockConstraints(factory));
        addAll(all, staffAvailabilityBlockConstraints(factory));

        // Soft rules — each rule registers HARD / MEDIUM / SOFT variants
        addAll(all, preferredDaysOffConstraints(factory));
        addAll(all, preferredShiftTypeConstraints(factory));
        addAll(all, honourRequestedLeaveConstraints(factory));
        addAll(all, fairHoursDistributionConstraints(factory));
        addAll(all, fairWeekendDistributionConstraints(factory));
        addAll(all, fairNightShiftDistributionConstraints(factory));
        addAll(all, minimiseUnderstaffingConstraints(factory));
        addAll(all, minimiseOverstaffingConstraints(factory));
        addAll(all, avoidExcessiveConsecutiveDaysConstraints(factory));
        addAll(all, softMaxHoursPerPeriodConstraints(factory));

        return all.toArray(Constraint[]::new);
    }

    // =========================================================================
    // Fan-out helpers
    // =========================================================================

    private static HardMediumSoftScore scoreFor(ConstraintLevel level) {
        return switch (level) {
            case HARD   -> HardMediumSoftScore.ofHard(1);
            case MEDIUM -> HardMediumSoftScore.ofMedium(1);
            case SOFT   -> HardMediumSoftScore.ONE_SOFT;
        };
    }

    private static List<Constraint> forAllLevels(
            ConstraintFactory factory,
            BiFunction<ConstraintFactory, ConstraintLevel, Constraint> fn) {
        return List.of(
                fn.apply(factory, ConstraintLevel.HARD),
                fn.apply(factory, ConstraintLevel.MEDIUM),
                fn.apply(factory, ConstraintLevel.SOFT)
        );
    }

    // =========================================================================
    // Cross-site blocking (always hard, no RuleConfiguration gate)
    // =========================================================================

    /**
     * A staff member committed to another site's published or solved roster
     * must not be assigned to any shift at this site during that period.
     * This constraint is unconditional and is never subject to per-site rule configuration.
     */
    private Constraint crossSiteBlocking(ConstraintFactory factory) {
        return factory.forEach(CrossSiteBlockingPeriod.class)
                .join(ShiftAssignment.class,
                        Joiners.equal(CrossSiteBlockingPeriod::staff, ShiftAssignment::getStaff),
                        Joiners.filtering((block, sa) ->
                                // Timefold may produce unassigned (staff == null) slots during
                                // incremental moves; skip them to avoid NPE inside the join.
                                sa.getStaff() != null
                                && sa.getShift().getStartDatetime().isBefore(block.endDatetime())
                                && sa.getShift().getEndDatetime().isAfter(block.startDatetime())))
                .penalize(HardMediumSoftScore.ofHard(1))
                .asConstraint(ConstraintDefaults.CROSS_SITE_BLOCKING);
    }

    // =========================================================================
    // MIN_REST_BETWEEN_SHIFTS
    // =========================================================================

    /**
     * Staff must have a minimum number of rest hours between the end of one shift
     * and the start of the next.  Parameter: {@code minimumRestHours} (default 10).
     */
    private List<Constraint> minRestBetweenShiftsConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::minRestBetweenShifts);
    }

    private Constraint minRestBetweenShifts(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.MIN_REST_BETWEEN_SHIFTS.name() + "_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.MIN_REST_BETWEEN_SHIFTS
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(ShiftAssignment.class,
                        Joiners.filtering((rc, sa) -> sa.getStaff() != null))
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, sa1) -> sa1.getStaff(), ShiftAssignment::getStaff),
                        Joiners.lessThan(
                                (rc, sa1) -> sa1.getShift().getEndDatetime(),
                                sa2 -> sa2.getShift().getStartDatetime()),
                        Joiners.filtering((rc, sa1, sa2) -> {
                            long restHours = ChronoUnit.HOURS.between(
                                    sa1.getShift().getEndDatetime(),
                                    sa2.getShift().getStartDatetime());
                            return restHours < rc.getIntParam(ConstraintDefaults.KEY_MINIMUM_REST_HOURS,
                                    ConstraintDefaults.DEFAULT_MINIMUM_REST_HOURS);
                        }))
                .penalize(score,
                        (rc, sa1, sa2) -> level == ConstraintLevel.SOFT ? rc.weightOrDefault(ConstraintDefaults.DEFAULT_WEIGHT) : 1)
                .asConstraint(name);
    }

    // =========================================================================
    // MAX_HOURS_PER_DAY
    // =========================================================================

    /**
     * A staff member must not work more than the configured maximum hours in a single day.
     * Parameter: {@code maximumHours} (default 12).
     * Penalty magnitude: each excess hour counts as one violation unit.
     */
    private List<Constraint> maxHoursPerDayConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::maxHoursPerDay);
    }

    private Constraint maxHoursPerDay(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.MAX_HOURS_PER_DAY.name() + "_" + level.name();
        return factory.forEach(ShiftAssignment.class)
                .filter(sa -> sa.getStaff() != null)
                .groupBy(ShiftAssignment::getStaff,
                        sa -> sa.getShift().getStartDatetime().toLocalDate(),
                        ConstraintCollectors.sum(sa -> shiftMinutes(sa.getShift())))
                .join(RuleConfiguration.class,
                        Joiners.filtering((staff, date, minutes, rc) ->
                                rc.getRuleType() == RuleType.MAX_HOURS_PER_DAY
                                && rc.isEnabled()
                                && rc.getConstraintLevel() == level))
                .filter((staff, date, minutes, rc) ->
                        minutes > rc.getIntParam(ConstraintDefaults.KEY_MAXIMUM_HOURS, ConstraintDefaults.DEFAULT_MAX_HOURS_PER_DAY) * 60)
                .penalize(score,
                        (staff, date, minutes, rc) -> {
                            int excessHours = (minutes / 60) - rc.getIntParam(ConstraintDefaults.KEY_MAXIMUM_HOURS, ConstraintDefaults.DEFAULT_MAX_HOURS_PER_DAY);
                            return level == ConstraintLevel.SOFT
                                    ? excessHours * rc.weightOrDefault(ConstraintDefaults.DEFAULT_WEIGHT)
                                    : excessHours;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // MAX_HOURS_PER_WEEK
    // =========================================================================

    /**
     * A staff member must not work more than the configured maximum hours in a single ISO week.
     * Parameter: {@code maximumHours} (default 38).
     */
    private List<Constraint> maxHoursPerWeekConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::maxHoursPerWeek);
    }

    private Constraint maxHoursPerWeek(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.MAX_HOURS_PER_WEEK.name() + "_" + level.name();
        return factory.forEach(ShiftAssignment.class)
                .filter(sa -> sa.getStaff() != null)
                .groupBy(ShiftAssignment::getStaff,
                        sa -> isoWeekStart(sa.getShift().getStartDatetime().toLocalDate()),
                        ConstraintCollectors.sum(sa -> shiftMinutes(sa.getShift())))
                .join(RuleConfiguration.class,
                        Joiners.filtering((staff, weekStart, minutes, rc) ->
                                rc.getRuleType() == RuleType.MAX_HOURS_PER_WEEK
                                && rc.isEnabled()
                                && rc.getConstraintLevel() == level))
                .filter((staff, weekStart, minutes, rc) ->
                        minutes > rc.getIntParam(ConstraintDefaults.KEY_MAXIMUM_HOURS, ConstraintDefaults.DEFAULT_MAX_HOURS_PER_WEEK) * 60)
                .penalize(score,
                        (staff, weekStart, minutes, rc) -> {
                            int excessHours = (minutes / 60) - rc.getIntParam(ConstraintDefaults.KEY_MAXIMUM_HOURS, ConstraintDefaults.DEFAULT_MAX_HOURS_PER_WEEK);
                            return level == ConstraintLevel.SOFT
                                    ? excessHours * rc.weightOrDefault(ConstraintDefaults.DEFAULT_WEIGHT)
                                    : excessHours;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // MAX_CONSECUTIVE_DAYS
    // =========================================================================

    /**
     * A staff member must not work more than the configured number of consecutive days.
     * Parameter: {@code maximumDays} (default 5).
     *
     * <p>Algorithm: assignments are deduplicated to (staff, date) pairs. For each worked
     * date D, the count of distinct worked dates in the look-back window
     * {@code (D - maximumDays, D - 1]} is computed.  If the count equals {@code maximumDays}
     * then every day in the window was worked, meaning D is the (maximumDays + 1)th
     * consecutive day — a violation.</p>
     */
    private List<Constraint> maxConsecutiveDaysConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::maxConsecutiveDays);
    }

    private Constraint maxConsecutiveDays(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.MAX_CONSECUTIVE_DAYS.name() + "_" + level.name();

        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.MAX_CONSECUTIVE_DAYS
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(ShiftAssignment.class,
                        Joiners.filtering((rc, sa) -> sa.getStaff() != null))
                // BiStream<RC, SA1>
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, sa1) -> sa1.getStaff(), sa2 -> sa2.getStaff()),
                        Joiners.filtering((rc, sa1, sa2) -> {
                            if (sa2.getStaff() == null) return false;
                            LocalDate d1 = sa1.getShift().getStartDatetime().toLocalDate();
                            LocalDate d2 = sa2.getShift().getStartDatetime().toLocalDate();
                            int maxDays = rc.getIntParam(ConstraintDefaults.KEY_MAXIMUM_DAYS, ConstraintDefaults.DEFAULT_MAX_CONSECUTIVE_DAYS);
                            long diff = ChronoUnit.DAYS.between(d2, d1);
                            return diff > 0 && diff <= maxDays;
                        }))
                // TriStream<RC, SA1, SA2>
                .groupBy(
                        (rc, sa1, sa2) -> sa1.getStaff(),
                        (rc, sa1, sa2) -> sa1.getShift().getStartDatetime().toLocalDate(),
                        (rc, sa1, sa2) -> rc,
                        ConstraintCollectors.countLongTri())
                // QuadStream<Staff, LocalDate, RC, Long>
                .filter((staff, date, rc, count) -> count >= rc.getIntParam(ConstraintDefaults.KEY_MAXIMUM_DAYS, ConstraintDefaults.DEFAULT_MAX_CONSECUTIVE_DAYS))
                .penalize(score,
                        (staff, date, rc, count) ->
                                level == ConstraintLevel.SOFT ? rc.weightOrDefault(ConstraintDefaults.DEFAULT_WEIGHT) : 1)
                .asConstraint(name);
    }

    // =========================================================================
    // MIN_STAFF_PER_SHIFT
    // =========================================================================

    /**
     * Each shift must have at least {@link Shift#getMinimumStaff()} assigned staff.
     * Penalty magnitude: number of missing staff.
     */
    private List<Constraint> minStaffPerShiftConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::minStaffPerShift);
    }

    private Constraint minStaffPerShift(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.MIN_STAFF_PER_SHIFT.name() + "_" + level.name();
        return factory.forEach(ShiftAssignment.class)
                .groupBy(ShiftAssignment::getShift,
                        ConstraintCollectors.sum(sa -> sa.getStaff() != null ? 1 : 0))
                .join(RuleConfiguration.class,
                        Joiners.filtering((shift, count, rc) ->
                                rc.getRuleType() == RuleType.MIN_STAFF_PER_SHIFT
                                && rc.isEnabled()
                                && rc.getConstraintLevel() == level))
                .filter((shift, count, rc) -> count < shift.getMinimumStaff())
                .penalize(score,
                        (shift, count, rc) -> {
                            int missing = shift.getMinimumStaff() - count;
                            return level == ConstraintLevel.SOFT
                                    ? missing * rc.weightOrDefault(ConstraintDefaults.DEFAULT_WEIGHT)
                                    : missing;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // MIN_QUALIFIED_STAFF_PER_SHIFT
    // =========================================================================

    /**
     * For each {@link ShiftQualificationRequirement}, the shift must have at least
     * {@code minimumCount} assigned staff who hold the required qualification.
     */
    private List<Constraint> minQualifiedStaffPerShiftConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::minQualifiedStaffPerShift);
    }

    private Constraint minQualifiedStaffPerShift(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.MIN_QUALIFIED_STAFF_PER_SHIFT.name() + "_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.MIN_QUALIFIED_STAFF_PER_SHIFT
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(ShiftQualificationRequirement.class)
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, req) -> req.getShift(), ShiftAssignment::getShift),
                        Joiners.filtering((rc, req, sa) -> sa.getStaff() != null))
                .ifExists(StaffQualification.class,
                        Joiners.equal((rc, req, sa) -> sa.getStaff(), StaffQualification::getStaff),
                        Joiners.equal((rc, req, sa) -> req.getQualification(),
                                StaffQualification::getQualification))
                // TriStream: only tuples where the assigned staff has the qualification
                .groupBy(
                        (rc, req, sa) -> req,
                        (rc, req, sa) -> rc,
                        ConstraintCollectors.sumLong((rc, req, sa) -> 1L))
                // TriStream<ShiftQualReq, RC, Long(qualifiedCount)>
                .filter((req, rc, count) -> count < req.getMinimumCount())
                .penalize(score,
                        (req, rc, count) -> {
                            int missing = (int)(req.getMinimumCount() - count);
                            return level == ConstraintLevel.SOFT
                                    ? missing * rc.weightOrDefault(ConstraintDefaults.DEFAULT_WEIGHT)
                                    : missing;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // QUALIFICATION_REQUIRED_FOR_SHIFT
    // =========================================================================

    /**
     * When a shift has a qualification requirement, every assigned staff member
     * must hold that qualification (not just the minimum count).
     */
    private List<Constraint> qualificationRequiredForShiftConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::qualificationRequiredForShift);
    }

    private Constraint qualificationRequiredForShift(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.QUALIFICATION_REQUIRED_FOR_SHIFT.name() + "_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.QUALIFICATION_REQUIRED_FOR_SHIFT
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(ShiftQualificationRequirement.class)
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, req) -> req.getShift(), ShiftAssignment::getShift),
                        Joiners.filtering((rc, req, sa) -> sa.getStaff() != null))
                .ifNotExists(StaffQualification.class,
                        Joiners.equal((rc, req, sa) -> sa.getStaff(), StaffQualification::getStaff),
                        Joiners.equal((rc, req, sa) -> req.getQualification(),
                                StaffQualification::getQualification))
                .penalize(score,
                        (rc, req, sa) ->
                                level == ConstraintLevel.SOFT ? rc.weightOrDefault(ConstraintDefaults.DEFAULT_WEIGHT) : 1)
                .asConstraint(name);
    }

    // =========================================================================
    // STAFF_MUTUAL_EXCLUSION
    // =========================================================================

    /**
     * Two staff members recorded in {@link StaffIncompatibility} must never be
     * assigned to the same shift.
     */
    private List<Constraint> staffMutualExclusionConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::staffMutualExclusion);
    }

    private Constraint staffMutualExclusion(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.STAFF_MUTUAL_EXCLUSION.name() + "_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.STAFF_MUTUAL_EXCLUSION
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(StaffIncompatibility.class)
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, inc) -> inc.getStaffA(), ShiftAssignment::getStaff))
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, inc, saA) -> saA.getShift(), ShiftAssignment::getShift),
                        Joiners.equal((rc, inc, saA) -> inc.getStaffB(), ShiftAssignment::getStaff))
                .penalize(score,
                        (rc, inc, saA, saB) ->
                                level == ConstraintLevel.SOFT ? rc.weightOrDefault(ConstraintDefaults.DEFAULT_WEIGHT) : 1)
                .asConstraint(name);
    }

    // =========================================================================
    // STAFF_MUST_PAIR
    // =========================================================================

    /**
     * Two staff members recorded in {@link StaffPairing} must always be assigned
     * to the same shift together.  Both directions are checked:
     * A assigned without B, and B assigned without A.
     */
    private List<Constraint> staffMustPairConstraints(ConstraintFactory factory) {
        List<Constraint> all = new ArrayList<>(forAllLevels(factory, this::staffMustPairAWithoutB));
        all.addAll(forAllLevels(factory, this::staffMustPairBWithoutA));
        return all;
    }

    private Constraint staffMustPairAWithoutB(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.STAFF_MUST_PAIR.name() + "_A_WITHOUT_B_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.STAFF_MUST_PAIR
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(StaffPairing.class)
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, pair) -> pair.getStaffA(), ShiftAssignment::getStaff))
                .ifNotExists(ShiftAssignment.class,
                        Joiners.equal((rc, pair, saA) -> saA.getShift(), ShiftAssignment::getShift),
                        Joiners.equal((rc, pair, saA) -> pair.getStaffB(), ShiftAssignment::getStaff))
                .penalize(score,
                        (rc, pair, saA) ->
                                level == ConstraintLevel.SOFT ? rc.weightOrDefault(ConstraintDefaults.DEFAULT_WEIGHT) : 1)
                .asConstraint(name);
    }

    private Constraint staffMustPairBWithoutA(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.STAFF_MUST_PAIR.name() + "_B_WITHOUT_A_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.STAFF_MUST_PAIR
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(StaffPairing.class)
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, pair) -> pair.getStaffB(), ShiftAssignment::getStaff))
                .ifNotExists(ShiftAssignment.class,
                        Joiners.equal((rc, pair, saB) -> saB.getShift(), ShiftAssignment::getShift),
                        Joiners.equal((rc, pair, saB) -> pair.getStaffA(), ShiftAssignment::getStaff))
                .penalize(score,
                        (rc, pair, saB) ->
                                level == ConstraintLevel.SOFT ? rc.weightOrDefault(1) : 1)
                .asConstraint(name);
    }

    // =========================================================================
    // STAFF_LEAVE_BLOCK
    // =========================================================================

    /**
     * Staff with {@link LeaveStatus#APPROVED} leave must not be assigned to any shift
     * whose date range overlaps the leave period.  This rule is always hard by design
     * but is configurable to other levels for exceptional site configurations.
     */
    private List<Constraint> staffLeaveBlockConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::staffLeaveBlock);
    }

    private Constraint staffLeaveBlock(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.STAFF_LEAVE_BLOCK.name() + "_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.STAFF_LEAVE_BLOCK
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(Leave.class,
                        Joiners.filtering((rc, leave) -> leave.getStatus() == LeaveStatus.APPROVED))
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, leave) -> leave.getStaff(), ShiftAssignment::getStaff),
                        Joiners.filtering((rc, leave, sa) ->
                                sa.getStaff() != null && shiftOverlapsLeave(sa.getShift(), leave)))
                .penalize(score,
                        (rc, leave, sa) ->
                                level == ConstraintLevel.SOFT ? rc.weightOrDefault(ConstraintDefaults.DEFAULT_WEIGHT) : 1)
                .asConstraint(name);
    }

    // =========================================================================
    // STAFF_AVAILABILITY_BLOCK
    // =========================================================================

    /**
     * Staff must not be assigned to shifts outside their declared availability windows.
     * Only fires for staff who have at least one {@link StaffAvailability} record
     * for the shift's day-of-week — staff with no availability data are assumed
     * to have unconstrained availability.
     *
     * <p>This rule defaults to HARD but may be set to MEDIUM for sites where
     * availability is advisory rather than absolute.</p>
     */
    private List<Constraint> staffAvailabilityBlockConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::staffAvailabilityBlock);
    }

    private Constraint staffAvailabilityBlock(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.STAFF_AVAILABILITY_BLOCK.name() + "_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.STAFF_AVAILABILITY_BLOCK
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(ShiftAssignment.class,
                        Joiners.filtering((rc, sa) -> sa.getStaff() != null))
                // Guard: only check staff who have at least one availability record for this day
                .ifExists(StaffAvailability.class,
                        Joiners.equal((rc, sa) -> sa.getStaff(), StaffAvailability::getStaff),
                        Joiners.filtering((rc, sa, avail) ->
                                avail.getDayOfWeek() == sa.getShift().getStartDatetime().getDayOfWeek()))
                // Penalise: no available window covers the full shift time on this day
                .ifNotExists(StaffAvailability.class,
                        Joiners.equal((rc, sa) -> sa.getStaff(), StaffAvailability::getStaff),
                        Joiners.filtering((rc, sa, avail) ->
                                avail.isAvailable()
                                && avail.getDayOfWeek() == sa.getShift().getStartDatetime().getDayOfWeek()
                                && !sa.getShift().getStartDatetime().toLocalTime()
                                        .isBefore(avail.getStartTime())
                                && !sa.getShift().getEndDatetime().toLocalTime()
                                        .isAfter(avail.getEndTime())))
                .penalize(score,
                        (rc, sa) ->
                                level == ConstraintLevel.SOFT ? rc.weightOrDefault(ConstraintDefaults.DEFAULT_WEIGHT) : 1)
                .asConstraint(name);
    }

    // =========================================================================
    // PREFERRED_DAYS_OFF
    // =========================================================================

    /**
     * Staff should not be assigned to shifts on their preferred days off.
     * Evaluated against {@link PreferenceType#PREFERRED_DAY_OFF} preferences.
     * Parameter: {@code penaltyPerViolation} (default 1).
     */
    private List<Constraint> preferredDaysOffConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::preferredDaysOff);
    }

    private Constraint preferredDaysOff(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.PREFERRED_DAYS_OFF.name() + "_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.PREFERRED_DAYS_OFF
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(StaffPreference.class,
                        Joiners.filtering((rc, pref) ->
                                pref.getPreferenceType() == PreferenceType.PREFERRED_DAY_OFF))
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, pref) -> pref.getStaff(), ShiftAssignment::getStaff),
                        Joiners.equal((rc, pref) -> pref.getDayOfWeek(),
                                sa -> sa.getShift().getStartDatetime().getDayOfWeek()),
                        Joiners.filtering((rc, pref, sa) -> sa.getStaff() != null))
                .penalize(score,
                        (rc, pref, sa) -> {
                            int penalty = rc.getIntParam(ConstraintDefaults.KEY_PENALTY_PER_VIOLATION, ConstraintDefaults.DEFAULT_PENALTY_PER_VIOLATION);
                            return level == ConstraintLevel.SOFT
                                    ? penalty * rc.weightOrDefault(ConstraintDefaults.DEFAULT_PENALTY_PER_VIOLATION)
                                    : penalty;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // PREFERRED_SHIFT_TYPE
    // =========================================================================

    /**
     * Penalises assignments that violate shift-type preferences:
     * <ul>
     *   <li>PREFERRED_SHIFT_TYPE: staff assigned to a shift of a different type than preferred.</li>
     *   <li>AVOID_SHIFT_TYPE: staff assigned to a shift of a type they want to avoid.</li>
     * </ul>
     * Only evaluated when the shift has a non-null {@link ShiftType}.
     */
    private List<Constraint> preferredShiftTypeConstraints(ConstraintFactory factory) {
        List<Constraint> all = new ArrayList<>(forAllLevels(factory, this::preferredShiftTypePrefer));
        all.addAll(forAllLevels(factory, this::preferredShiftTypeAvoid));
        return all;
    }

    private Constraint preferredShiftTypePrefer(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.PREFERRED_SHIFT_TYPE.name() + "_PREFER_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.PREFERRED_SHIFT_TYPE
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(StaffPreference.class,
                        Joiners.filtering((rc, pref) ->
                                pref.getPreferenceType() == PreferenceType.PREFERRED_SHIFT_TYPE))
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, pref) -> pref.getStaff(), ShiftAssignment::getStaff),
                        Joiners.filtering((rc, pref, sa) ->
                                sa.getStaff() != null
                                && sa.getShift().getShiftType() != null
                                && !sa.getShift().getShiftType().equals(pref.getShiftType())))
                .penalize(score,
                        (rc, pref, sa) -> {
                            int penalty = rc.getIntParam(ConstraintDefaults.KEY_PENALTY_PER_VIOLATION, ConstraintDefaults.DEFAULT_PENALTY_PER_VIOLATION);
                            return level == ConstraintLevel.SOFT
                                    ? penalty * rc.weightOrDefault(ConstraintDefaults.DEFAULT_PENALTY_PER_VIOLATION)
                                    : penalty;
                        })
                .asConstraint(name);
    }

    private Constraint preferredShiftTypeAvoid(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.PREFERRED_SHIFT_TYPE.name() + "_AVOID_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.PREFERRED_SHIFT_TYPE
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(StaffPreference.class,
                        Joiners.filtering((rc, pref) ->
                                pref.getPreferenceType() == PreferenceType.AVOID_SHIFT_TYPE))
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, pref) -> pref.getStaff(), ShiftAssignment::getStaff),
                        Joiners.filtering((rc, pref, sa) ->
                                sa.getStaff() != null
                                && sa.getShift().getShiftType() != null
                                && sa.getShift().getShiftType().equals(pref.getShiftType())))
                .penalize(score,
                        (rc, pref, sa) -> {
                            int penalty = rc.getIntParam(ConstraintDefaults.KEY_PENALTY_PER_VIOLATION, ConstraintDefaults.DEFAULT_PENALTY_PER_VIOLATION);
                            return level == ConstraintLevel.SOFT
                                    ? penalty * rc.weightOrDefault(ConstraintDefaults.DEFAULT_PENALTY_PER_VIOLATION)
                                    : penalty;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // HONOUR_REQUESTED_LEAVE
    // =========================================================================

    /**
     * Staff with {@link LeaveStatus#REQUESTED} (unconfirmed) leave should not be
     * assigned to shifts overlapping the leave period.
     * Softer than {@code STAFF_LEAVE_BLOCK}; the solver may violate this when needed.
     * Parameter: {@code penaltyPerViolation} (default 5).
     */
    private List<Constraint> honourRequestedLeaveConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::honourRequestedLeave);
    }

    private Constraint honourRequestedLeave(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.HONOUR_REQUESTED_LEAVE.name() + "_" + level.name();
        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.HONOUR_REQUESTED_LEAVE
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(Leave.class,
                        Joiners.filtering((rc, leave) -> leave.getStatus() == LeaveStatus.REQUESTED))
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, leave) -> leave.getStaff(), ShiftAssignment::getStaff),
                        Joiners.filtering((rc, leave, sa) ->
                                sa.getStaff() != null && shiftOverlapsLeave(sa.getShift(), leave)))
                .penalize(score,
                        (rc, leave, sa) -> {
                            int penalty = rc.getIntParam(ConstraintDefaults.KEY_PENALTY_PER_VIOLATION, ConstraintDefaults.DEFAULT_PENALTY_REQUESTED_LEAVE);
                            return level == ConstraintLevel.SOFT
                                    ? penalty * rc.weightOrDefault(ConstraintDefaults.DEFAULT_PENALTY_REQUESTED_LEAVE)
                                    : penalty;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // FAIR_HOURS_DISTRIBUTION
    // =========================================================================

    /**
     * Penalises rosters where the spread between the most-worked and least-worked
     * staff members exceeds {@code maximumDeviationHours} (default 4).
     *
     * <p>All per-staff hour totals are collected into a single {@code Map} and evaluated
     * in one pass, yielding a penalty equal to the excess spread beyond the threshold.</p>
     */
    private List<Constraint> fairHoursDistributionConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::fairHoursDistribution);
    }

    private Constraint fairHoursDistribution(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.FAIR_HOURS_DISTRIBUTION.name() + "_" + level.name();

        var staffMinutesMap = factory.forEach(ShiftAssignment.class)
                .filter(sa -> sa.getStaff() != null)
                .groupBy(ShiftAssignment::getStaff,
                        ConstraintCollectors.sum(sa -> shiftMinutes(sa.getShift())))
                .groupBy(ConstraintCollectors.toMap(
                        (staff, mins) -> staff,
                        (staff, mins) -> mins,
                        (a, b) -> a));
        // UniStream<Map<Staff, Integer>>

        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.FAIR_HOURS_DISTRIBUTION
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(staffMinutesMap)
                .penalize(score,
                        (rc, minutesMap) -> {
                            int maxDeviationMins = rc.getIntParam(ConstraintDefaults.KEY_MAX_DEVIATION_HOURS, ConstraintDefaults.DEFAULT_MAX_DEVIATION_HOURS) * 60;
                            long excess = spreadMinutes(minutesMap) - maxDeviationMins;
                            if (excess <= 0) return 0;
                            int excessHours = (int)(excess / 60);
                            return level == ConstraintLevel.SOFT
                                    ? excessHours * rc.weightOrDefault(ConstraintDefaults.DEFAULT_FAIR_HOURS_WEIGHT)
                                    : excessHours;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // FAIR_WEEKEND_DISTRIBUTION
    // =========================================================================

    /**
     * Penalises rosters where the spread in weekend shift counts between staff members
     * exceeds {@code maximumDeviationShifts} (default 2).
     */
    private List<Constraint> fairWeekendDistributionConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::fairWeekendDistribution);
    }

    private Constraint fairWeekendDistribution(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.FAIR_WEEKEND_DISTRIBUTION.name() + "_" + level.name();

        var staffWeekendMap = factory.forEach(ShiftAssignment.class)
                .filter(sa -> sa.getStaff() != null && isWeekend(sa.getShift()))
                .groupBy(ShiftAssignment::getStaff,
                        ConstraintCollectors.count())
                .groupBy(ConstraintCollectors.toMap(
                        (staff, count) -> staff,
                        (staff, count) -> count,
                        (a, b) -> a));

        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.FAIR_WEEKEND_DISTRIBUTION
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(staffWeekendMap)
                .penalize(score,
                        (rc, countMap) -> {
                            int maxDeviation = rc.getIntParam(ConstraintDefaults.KEY_MAX_DEVIATION_SHIFTS, ConstraintDefaults.DEFAULT_MAX_DEVIATION_SHIFTS);
                            long excess = spreadCount(countMap) - maxDeviation;
                            if (excess <= 0) return 0;
                            return (int)(level == ConstraintLevel.SOFT
                                    ? excess * rc.weightOrDefault(ConstraintDefaults.DEFAULT_FAIR_SHIFT_WEIGHT)
                                    : excess);
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // FAIR_NIGHT_SHIFT_DISTRIBUTION
    // =========================================================================

    /**
     * Penalises rosters where the spread in night-shift counts between staff members
     * exceeds {@code maximumDeviationShifts} (default 2).
     * A shift is classified as a night shift when its start time is at or after 20:00.
     */
    private List<Constraint> fairNightShiftDistributionConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::fairNightShiftDistribution);
    }

    private Constraint fairNightShiftDistribution(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.FAIR_NIGHT_SHIFT_DISTRIBUTION.name() + "_" + level.name();

        var staffNightMap = factory.forEach(ShiftAssignment.class)
                .filter(sa -> sa.getStaff() != null && isNightShift(sa.getShift()))
                .groupBy(ShiftAssignment::getStaff,
                        ConstraintCollectors.count())
                .groupBy(ConstraintCollectors.toMap(
                        (staff, count) -> staff,
                        (staff, count) -> count,
                        (a, b) -> a));

        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.FAIR_NIGHT_SHIFT_DISTRIBUTION
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(staffNightMap)
                .penalize(score,
                        (rc, countMap) -> {
                            int maxDeviation = rc.getIntParam(ConstraintDefaults.KEY_MAX_DEVIATION_SHIFTS, ConstraintDefaults.DEFAULT_MAX_DEVIATION_SHIFTS);
                            long excess = spreadCount(countMap) - maxDeviation;
                            if (excess <= 0) return 0;
                            return (int)(level == ConstraintLevel.SOFT
                                    ? excess * rc.weightOrDefault(ConstraintDefaults.DEFAULT_FAIR_SHIFT_WEIGHT)
                                    : excess);
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // MINIMISE_UNDERSTAFFING
    // =========================================================================

    /**
     * Penalises shifts where the assigned staff count is below {@link Shift#getMinimumStaff()}.
     * Complements the hard {@code MIN_STAFF_PER_SHIFT} rule — when the hard rule fires,
     * this soft rule additionally guides the solver to minimise the shortfall.
     * Parameter: {@code penaltyPerMissingStaff} (default 10).
     */
    private List<Constraint> minimiseUnderstaffingConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::minimiseUnderstaffing);
    }

    private Constraint minimiseUnderstaffing(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.MINIMISE_UNDERSTAFFING.name() + "_" + level.name();
        return factory.forEach(ShiftAssignment.class)
                .groupBy(ShiftAssignment::getShift,
                        ConstraintCollectors.sum(sa -> sa.getStaff() != null ? 1 : 0))
                .join(RuleConfiguration.class,
                        Joiners.filtering((shift, count, rc) ->
                                rc.getRuleType() == RuleType.MINIMISE_UNDERSTAFFING
                                && rc.isEnabled()
                                && rc.getConstraintLevel() == level))
                .filter((shift, count, rc) -> count < shift.getMinimumStaff())
                .penalize(score,
                        (shift, count, rc) -> {
                            int missing = shift.getMinimumStaff() - count;
                            int perStaff = rc.getIntParam(ConstraintDefaults.KEY_PENALTY_MISSING_STAFF, ConstraintDefaults.DEFAULT_PENALTY_MISSING_STAFF);
                            return level == ConstraintLevel.SOFT
                                    ? missing * perStaff * rc.weightOrDefault(ConstraintDefaults.DEFAULT_PENALTY_MISSING_STAFF)
                                    : missing * perStaff;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // MINIMISE_OVERSTAFFING
    // =========================================================================

    /**
     * Penalises shifts where the assigned staff count exceeds {@link Shift#getMinimumStaff()}.
     * Parameter: {@code penaltyPerExtraStaff} (default 1).
     */
    private List<Constraint> minimiseOverstaffingConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::minimiseOverstaffing);
    }

    private Constraint minimiseOverstaffing(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.MINIMISE_OVERSTAFFING.name() + "_" + level.name();
        return factory.forEach(ShiftAssignment.class)
                .filter(sa -> sa.getStaff() != null)
                .groupBy(ShiftAssignment::getShift,
                        ConstraintCollectors.count())
                .join(RuleConfiguration.class,
                        Joiners.filtering((shift, count, rc) ->
                                rc.getRuleType() == RuleType.MINIMISE_OVERSTAFFING
                                && rc.isEnabled()
                                && rc.getConstraintLevel() == level))
                .filter((shift, count, rc) -> count > shift.getMinimumStaff())
                .penalize(score,
                        (shift, count, rc) -> {
                            int excess = count - shift.getMinimumStaff();
                            int perStaff = rc.getIntParam(ConstraintDefaults.KEY_PENALTY_EXTRA_STAFF, ConstraintDefaults.DEFAULT_PENALTY_EXTRA_STAFF);
                            return level == ConstraintLevel.SOFT
                                    ? excess * perStaff * rc.weightOrDefault(ConstraintDefaults.DEFAULT_PENALTY_EXTRA_STAFF)
                                    : excess * perStaff;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // AVOID_EXCESSIVE_CONSECUTIVE_DAYS
    // =========================================================================

    /**
     * Penalises each worked day that extends a run beyond the preferred maximum.
     * Softer than {@code MAX_CONSECUTIVE_DAYS}; uses parameter {@code preferredMaxDays}
     * (default 4) rather than the absolute maximum.
     * Parameter: {@code penaltyPerExtraDay} (default 2).
     *
     * @see #maxConsecutiveDays same algorithm; different parameter key
     */
    private List<Constraint> avoidExcessiveConsecutiveDaysConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::avoidExcessiveConsecutiveDays);
    }

    private Constraint avoidExcessiveConsecutiveDays(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.AVOID_EXCESSIVE_CONSECUTIVE_DAYS.name() + "_" + level.name();

        return factory.forEach(RuleConfiguration.class)
                .filter(rc -> rc.getRuleType() == RuleType.AVOID_EXCESSIVE_CONSECUTIVE_DAYS
                           && rc.isEnabled()
                           && rc.getConstraintLevel() == level)
                .join(ShiftAssignment.class,
                        Joiners.filtering((rc, sa) -> sa.getStaff() != null))
                // BiStream<RC, SA1>
                .join(ShiftAssignment.class,
                        Joiners.equal((rc, sa1) -> sa1.getStaff(), sa2 -> sa2.getStaff()),
                        Joiners.filtering((rc, sa1, sa2) -> {
                            if (sa2.getStaff() == null) return false;
                            LocalDate d1 = sa1.getShift().getStartDatetime().toLocalDate();
                            LocalDate d2 = sa2.getShift().getStartDatetime().toLocalDate();
                            int preferredMax = rc.getIntParam(ConstraintDefaults.KEY_PREFERRED_MAX_DAYS, ConstraintDefaults.DEFAULT_PREFERRED_MAX_DAYS);
                            long diff = ChronoUnit.DAYS.between(d2, d1);
                            return diff > 0 && diff <= preferredMax;
                        }))
                // TriStream<RC, SA1, SA2>
                .groupBy(
                        (rc, sa1, sa2) -> sa1.getStaff(),
                        (rc, sa1, sa2) -> sa1.getShift().getStartDatetime().toLocalDate(),
                        (rc, sa1, sa2) -> rc,
                        ConstraintCollectors.countLongTri())
                // QuadStream<Staff, LocalDate, RC, Long>
                .filter((staff, date, rc, count) ->
                        count >= rc.getIntParam(ConstraintDefaults.KEY_PREFERRED_MAX_DAYS, ConstraintDefaults.DEFAULT_PREFERRED_MAX_DAYS))
                .penalize(score,
                        (staff, date, rc, count) -> {
                            int perDay = rc.getIntParam(ConstraintDefaults.KEY_PENALTY_EXTRA_DAY, ConstraintDefaults.DEFAULT_PENALTY_EXTRA_DAY);
                            return level == ConstraintLevel.SOFT
                                    ? perDay * rc.weightOrDefault(ConstraintDefaults.DEFAULT_FAIR_SHIFT_WEIGHT)
                                    : perDay;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // SOFT_MAX_HOURS_PER_PERIOD
    // =========================================================================

    /**
     * Penalises staff members whose total hours over the roster period exceed the
     * configured soft maximum.
     * Parameter: {@code maximumHours} (default 76), {@code penaltyPerExtraHour} (default 3).
     */
    private List<Constraint> softMaxHoursPerPeriodConstraints(ConstraintFactory factory) {
        return forAllLevels(factory, this::softMaxHoursPerPeriod);
    }

    private Constraint softMaxHoursPerPeriod(ConstraintFactory factory, ConstraintLevel level) {
        HardMediumSoftScore score = scoreFor(level);
        String name = RuleType.SOFT_MAX_HOURS_PER_PERIOD.name() + "_" + level.name();
        return factory.forEach(ShiftAssignment.class)
                .filter(sa -> sa.getStaff() != null)
                .groupBy(ShiftAssignment::getStaff,
                        ConstraintCollectors.sum(sa -> shiftMinutes(sa.getShift())))
                .join(RuleConfiguration.class,
                        Joiners.filtering((staff, minutes, rc) ->
                                rc.getRuleType() == RuleType.SOFT_MAX_HOURS_PER_PERIOD
                                && rc.isEnabled()
                                && rc.getConstraintLevel() == level))
                .filter((staff, minutes, rc) ->
                        minutes > rc.getIntParam(ConstraintDefaults.KEY_MAXIMUM_HOURS, ConstraintDefaults.DEFAULT_MAX_HOURS_PER_PERIOD) * 60)
                .penalize(score,
                        (staff, minutes, rc) -> {
                            int excessHours = (minutes / 60) - rc.getIntParam(ConstraintDefaults.KEY_MAXIMUM_HOURS, ConstraintDefaults.DEFAULT_MAX_HOURS_PER_PERIOD);
                            int perHour = rc.getIntParam(ConstraintDefaults.KEY_PENALTY_EXTRA_HOUR, ConstraintDefaults.DEFAULT_PENALTY_EXTRA_HOUR);
                            return level == ConstraintLevel.SOFT
                                    ? excessHours * perHour * rc.weightOrDefault(ConstraintDefaults.DEFAULT_FAIR_HOURS_WEIGHT)
                                    : excessHours * perHour;
                        })
                .asConstraint(name);
    }

    // =========================================================================
    // Private utility methods
    // =========================================================================

    /** Returns the duration of a shift in whole minutes. */
    private static int shiftMinutes(Shift shift) {
        return Math.toIntExact(Duration.between(
                shift.getStartDatetime(), shift.getEndDatetime()).toMinutes());
    }

    /**
     * Returns the Monday of the ISO week containing the given date.
     * Used as a stable week-group key.
     */
    private static LocalDate isoWeekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /** Returns {@code true} when the shift starts on a Saturday or Sunday. */
    private static boolean isWeekend(Shift shift) {
        DayOfWeek dow = shift.getStartDatetime().getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    /**
     * Returns {@code true} when the shift is classified as a night shift.
     * Classification: start time at or after 20:00 (local time of the shift's timezone).
     */
    private static boolean isNightShift(Shift shift) {
        LocalTime start = shift.getStartDatetime().toLocalTime();
        return !start.isBefore(LocalTime.of(20, 0));
    }

    /**
     * Returns {@code true} when the shift's date range overlaps the given leave period.
     * Overlap test: {@code shift.start.date <= leave.end AND shift.end.date >= leave.start}.
     */
    private static boolean shiftOverlapsLeave(Shift shift, Leave leave) {
        LocalDate shiftStart = shift.getStartDatetime().toLocalDate();
        LocalDate shiftEnd   = shift.getEndDatetime().toLocalDate();
        return !shiftStart.isAfter(leave.getEndDate())
            && !shiftEnd.isBefore(leave.getStartDate());
    }

    /**
     * Returns the spread (max - min) in minutes across all staff from the given hours map.
     * Returns {@code 0} when the map has fewer than two entries.
     */
    private static long spreadMinutes(Map<Staff, Integer> minutesMap) {
        if (minutesMap.size() < 2) return 0L;
        int max = minutesMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = minutesMap.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        return max - min;
    }

    /**
     * Returns the spread (max - min) in shift count across all staff from the given count map.
     * Returns {@code 0} when the map has fewer than two entries.
     */
    private static long spreadCount(Map<Staff, Integer> countMap) {
        if (countMap.size() < 2) return 0L;
        int max = countMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = countMap.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        return max - min;
    }

    /** Adds all elements from a {@link List} into the target list. */
    private static void addAll(List<Constraint> target, List<Constraint> source) {
        target.addAll(source);
    }
}
