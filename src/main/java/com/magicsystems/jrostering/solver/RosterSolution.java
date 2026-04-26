package com.magicsystems.jrostering.solver;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import com.magicsystems.jrostering.domain.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * The Timefold planning solution for a roster period.
 *
 * <p>An instance of this class is built by {@code RosterSolutionMapper} before each
 * solve run and passed to the Timefold {@code SolverManager}. The solver modifies
 * the {@code staff} field of each {@link ShiftAssignment} planning entity in-place
 * and returns the best solution found within the time limit.</p>
 *
 * <h3>Score hierarchy — HardMediumSoftScore</h3>
 * <ul>
 *   <li><b>Hard</b> — must never be violated (leave blocks, qualifications, rest rules,
 *       availability when {@link ConstraintLevel#HARD}, cross-site blocking).</li>
 *   <li><b>Medium</b> — near-absolute; violated only when no feasible solution exists
 *       (availability when {@link ConstraintLevel#MEDIUM}).</li>
 *   <li><b>Soft</b> — weighted preference optimisation (fairness, preferences, staffing levels).</li>
 * </ul>
 *
 * <h3>Value range</h3>
 * <p>The {@code staffRange} provider exposes the list of eligible {@link Staff} members.
 * The planning variable on {@link ShiftAssignment} is nullable, allowing the solver to
 * leave a slot unassigned when no valid assignment exists.</p>
 */
@PlanningSolution
@Getter
@Setter
@NoArgsConstructor
public class RosterSolution {

    // =========================================================================
    // Problem facts — read-only inputs the solver evaluates constraints against
    // =========================================================================

    @ProblemFactCollectionProperty
    private List<Shift> shifts;

    /**
     * Also serves as the value range for the planning variable on {@link ShiftAssignment}.
     * The {@code @ValueRangeProvider} id must match the {@code valueRangeProviderRefs}
     * on {@link ShiftAssignment#getStaff()}.
     */
    @ValueRangeProvider(id = "staffRange")
    @ProblemFactCollectionProperty
    private List<Staff> staff;

    @ProblemFactCollectionProperty
    private List<ShiftType> shiftTypes;

    @ProblemFactCollectionProperty
    private List<Qualification> qualifications;

    @ProblemFactCollectionProperty
    private List<StaffQualification> staffQualifications;

    @ProblemFactCollectionProperty
    private List<StaffPreference> staffPreferences;

    @ProblemFactCollectionProperty
    private List<ShiftQualificationRequirement> shiftQualificationRequirements;

    /**
     * Contains both {@link LeaveStatus#APPROVED} and {@link LeaveStatus#REQUESTED} leave.
     * The constraint provider differentiates between them when evaluating
     * {@code STAFF_LEAVE_BLOCK} (hard) and {@code HONOUR_REQUESTED_LEAVE} (soft).
     */
    @ProblemFactCollectionProperty
    private List<Leave> leaveRecords;

    @ProblemFactCollectionProperty
    private List<StaffAvailability> staffAvailabilities;

    @ProblemFactCollectionProperty
    private List<StaffIncompatibility> staffIncompatibilities;

    @ProblemFactCollectionProperty
    private List<StaffPairing> staffPairings;

    /**
     * Confirmed shift commitments at other sites for multi-site staff members.
     * Built by {@code RosterSolutionMapper} from published/solved periods at other sites.
     * Evaluated as a hard constraint regardless of site-level rule configuration.
     */
    @ProblemFactCollectionProperty
    private List<CrossSiteBlockingPeriod> crossSiteBlockingPeriods;

    /**
     * All enabled rule configurations for the site being solved.
     * The constraint provider reads these to determine constraint levels and parameters.
     */
    @ProblemFactCollectionProperty
    private List<RuleConfiguration> ruleConfigurations;

    // =========================================================================
    // Planning entities — modified by the solver
    // =========================================================================

    /** The solver assigns a {@link Staff} value to each slot. */
    @PlanningEntityCollectionProperty
    private List<ShiftAssignment> shiftAssignments;

    // =========================================================================
    // Score
    // =========================================================================

    @PlanningScore
    private HardMediumSoftScore score;
}
