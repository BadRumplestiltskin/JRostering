package com.magicsystems.jrostering.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.magicsystems.jrostering.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Unit tests for {@link RosterConstraintProvider} using Timefold's
 * {@link ConstraintVerifier} API.
 *
 * <p>Each test creates the minimum set of domain facts required to trigger (or
 * not trigger) a specific constraint. Only the HARD constraint variant is exercised
 * per rule; MEDIUM and SOFT use the same penalty logic with a different score tier.</p>
 *
 * <p>All tests use {@code verifyThat().given(...).scores(...)} rather than
 * method references, since the constraint methods in {@link RosterConstraintProvider}
 * are private. Passing only the facts relevant to one constraint ensures no other
 * constraint fires, keeping the assertion focused.</p>
 *
 * <p>No Spring context or database is required — Timefold's test module provides
 * an in-memory constraint evaluator.</p>
 */
class RosterConstraintProviderTest {

    private ConstraintVerifier<RosterConstraintProvider, RosterSolution> verifier;

    @BeforeEach
    void setUp() {
        verifier = ConstraintVerifier.build(
                new RosterConstraintProvider(),
                RosterSolution.class,
                ShiftAssignment.class
        );
    }

    // =========================================================================
    // Domain-object builders
    // =========================================================================

    private static Staff makeStaff(long id) {
        Staff s = new Staff();
        s.setId(id);
        s.setFirstName("Staff");
        s.setLastName(String.valueOf(id));
        s.setActive(true);
        return s;
    }

    private static Shift makeShift(long id, OffsetDateTime start, OffsetDateTime end) {
        Shift shift = new Shift();
        shift.setId(id);
        shift.setStartDatetime(start);
        shift.setEndDatetime(end);
        shift.setMinimumStaff(1);
        return shift;
    }

    private static ShiftAssignment assign(long id, Shift shift, Staff staff) {
        ShiftAssignment sa = new ShiftAssignment();
        sa.setId(id);
        sa.setShift(shift);
        sa.setStaff(staff);
        return sa;
    }

    private static RuleConfiguration ruleConfig(RuleType ruleType) {
        RuleConfiguration rc = new RuleConfiguration();
        rc.setRuleType(ruleType);
        rc.setEnabled(true);
        rc.setConstraintLevel(ConstraintLevel.HARD);
        rc.setParameterJson("{}");
        return rc;
    }

    // =========================================================================
    // MIN_REST_BETWEEN_SHIFTS
    // =========================================================================

    @Test
    void minRestBetweenShifts_penalisesInsufficientRest() {
        Staff staff = makeStaff(1L);
        OffsetDateTime shift1End   = OffsetDateTime.of(2025, 5, 1, 20, 0, 0, 0, ZoneOffset.UTC);
        // 8-hour gap — below the 10-hour default minimum
        OffsetDateTime shift2Start = OffsetDateTime.of(2025, 5, 2, 4, 0, 0, 0, ZoneOffset.UTC);

        Shift shift1 = makeShift(1L, shift1End.minusHours(8), shift1End);
        Shift shift2 = makeShift(2L, shift2Start, shift2Start.plusHours(8));

        ShiftAssignment sa1 = assign(1L, shift1, staff);
        ShiftAssignment sa2 = assign(2L, shift2, staff);

        RuleConfiguration rc = ruleConfig(RuleType.MIN_REST_BETWEEN_SHIFTS);

        verifier.verifyThat()
                .given(rc, sa1, sa2)
                .scores(HardMediumSoftScore.of(-1, 0, 0));
    }

    @Test
    void minRestBetweenShifts_doesNotPenaliseSufficientRest() {
        Staff staff = makeStaff(1L);
        OffsetDateTime shift1End   = OffsetDateTime.of(2025, 5, 1, 20, 0, 0, 0, ZoneOffset.UTC);
        // 16-hour gap — well above the 10-hour default minimum
        OffsetDateTime shift2Start = OffsetDateTime.of(2025, 5, 2, 12, 0, 0, 0, ZoneOffset.UTC);

        Shift shift1 = makeShift(1L, shift1End.minusHours(8), shift1End);
        Shift shift2 = makeShift(2L, shift2Start, shift2Start.plusHours(8));

        ShiftAssignment sa1 = assign(1L, shift1, staff);
        ShiftAssignment sa2 = assign(2L, shift2, staff);

        RuleConfiguration rc = ruleConfig(RuleType.MIN_REST_BETWEEN_SHIFTS);

        verifier.verifyThat()
                .given(rc, sa1, sa2)
                .scores(HardMediumSoftScore.ZERO);
    }

    // =========================================================================
    // STAFF_LEAVE_BLOCK
    // =========================================================================

    @Test
    void staffLeaveBlock_penalisesAssignmentDuringApprovedLeave() {
        Staff staff = makeStaff(1L);

        Leave leave = new Leave();
        leave.setId(1L);
        leave.setStaff(staff);
        leave.setStartDate(LocalDate.of(2025, 5, 5));
        leave.setEndDate(LocalDate.of(2025, 5, 10));
        leave.setLeaveType(LeaveType.ANNUAL);
        leave.setStatus(LeaveStatus.APPROVED);

        // Shift on 7 May — squarely within the approved leave
        OffsetDateTime shiftStart = OffsetDateTime.of(2025, 5, 7, 8, 0, 0, 0, ZoneOffset.UTC);
        Shift shift = makeShift(1L, shiftStart, shiftStart.plusHours(8));
        ShiftAssignment sa = assign(1L, shift, staff);

        RuleConfiguration rc = ruleConfig(RuleType.STAFF_LEAVE_BLOCK);

        verifier.verifyThat()
                .given(rc, leave, sa)
                .scores(HardMediumSoftScore.of(-1, 0, 0));
    }

    @Test
    void staffLeaveBlock_doesNotPenaliseRejectedLeave() {
        Staff staff = makeStaff(1L);

        Leave leave = new Leave();
        leave.setId(1L);
        leave.setStaff(staff);
        leave.setStartDate(LocalDate.of(2025, 5, 5));
        leave.setEndDate(LocalDate.of(2025, 5, 10));
        leave.setLeaveType(LeaveType.ANNUAL);
        leave.setStatus(LeaveStatus.REJECTED);  // REJECTED — constraint does not fire

        OffsetDateTime shiftStart = OffsetDateTime.of(2025, 5, 7, 8, 0, 0, 0, ZoneOffset.UTC);
        Shift shift = makeShift(1L, shiftStart, shiftStart.plusHours(8));
        ShiftAssignment sa = assign(1L, shift, staff);

        RuleConfiguration rc = ruleConfig(RuleType.STAFF_LEAVE_BLOCK);

        verifier.verifyThat()
                .given(rc, leave, sa)
                .scores(HardMediumSoftScore.ZERO);
    }

    // =========================================================================
    // CROSS_SITE_BLOCKING
    // =========================================================================

    @Test
    void crossSiteBlocking_penalisesAssignmentOverlappingBlockingPeriod() {
        Staff staff = makeStaff(1L);

        OffsetDateTime blockStart = OffsetDateTime.of(2025, 5, 1, 8,  0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime blockEnd   = OffsetDateTime.of(2025, 5, 1, 20, 0, 0, 0, ZoneOffset.UTC);
        CrossSiteBlockingPeriod block = new CrossSiteBlockingPeriod(staff, blockStart, blockEnd);

        // Shift overlaps the blocking period
        OffsetDateTime shiftStart = OffsetDateTime.of(2025, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        Shift shift = makeShift(1L, shiftStart, shiftStart.plusHours(4));
        ShiftAssignment sa = assign(1L, shift, staff);

        // No RuleConfiguration required — cross-site blocking always fires
        verifier.verifyThat()
                .given(block, sa)
                .scores(HardMediumSoftScore.of(-1, 0, 0));
    }

    @Test
    void crossSiteBlocking_doesNotPenaliseNonOverlappingAssignment() {
        Staff staff = makeStaff(1L);

        OffsetDateTime blockStart = OffsetDateTime.of(2025, 5, 1, 8,  0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime blockEnd   = OffsetDateTime.of(2025, 5, 1, 20, 0, 0, 0, ZoneOffset.UTC);
        CrossSiteBlockingPeriod block = new CrossSiteBlockingPeriod(staff, blockStart, blockEnd);

        // Shift is the following day — no temporal overlap
        OffsetDateTime shiftStart = OffsetDateTime.of(2025, 5, 2, 8, 0, 0, 0, ZoneOffset.UTC);
        Shift shift = makeShift(1L, shiftStart, shiftStart.plusHours(8));
        ShiftAssignment sa = assign(1L, shift, staff);

        verifier.verifyThat()
                .given(block, sa)
                .scores(HardMediumSoftScore.ZERO);
    }

    // =========================================================================
    // STAFF_MUTUAL_EXCLUSION
    // =========================================================================

    @Test
    void staffMutualExclusion_penalisesIncompatiblePairOnSameShift() {
        Staff staffA = makeStaff(1L);
        Staff staffB = makeStaff(2L);

        StaffIncompatibility incompatibility = new StaffIncompatibility();
        incompatibility.setId(1L);
        incompatibility.setStaffA(staffA);
        incompatibility.setStaffB(staffB);

        OffsetDateTime shiftStart = OffsetDateTime.of(2025, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        Shift shift = makeShift(1L, shiftStart, shiftStart.plusHours(8));

        ShiftAssignment saA = assign(1L, shift, staffA);
        ShiftAssignment saB = assign(2L, shift, staffB);

        RuleConfiguration rc = ruleConfig(RuleType.STAFF_MUTUAL_EXCLUSION);

        verifier.verifyThat()
                .given(rc, incompatibility, saA, saB)
                .scores(HardMediumSoftScore.of(-1, 0, 0));
    }

    @Test
    void staffMutualExclusion_doesNotPenaliseIncompatiblePairOnDifferentShifts() {
        Staff staffA = makeStaff(1L);
        Staff staffB = makeStaff(2L);

        StaffIncompatibility incompatibility = new StaffIncompatibility();
        incompatibility.setId(1L);
        incompatibility.setStaffA(staffA);
        incompatibility.setStaffB(staffB);

        OffsetDateTime start = OffsetDateTime.of(2025, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        Shift shift1 = makeShift(1L, start, start.plusHours(8));
        Shift shift2 = makeShift(2L, start.plusDays(1), start.plusDays(1).plusHours(8));

        ShiftAssignment saA = assign(1L, shift1, staffA);
        ShiftAssignment saB = assign(2L, shift2, staffB);

        RuleConfiguration rc = ruleConfig(RuleType.STAFF_MUTUAL_EXCLUSION);

        verifier.verifyThat()
                .given(rc, incompatibility, saA, saB)
                .scores(HardMediumSoftScore.ZERO);
    }

    // =========================================================================
    // MIN_QUALIFIED_STAFF_PER_SHIFT
    // =========================================================================

    @Test
    void minQualifiedStaffPerShift_penalisesShortfall() {
        Staff staffA = makeStaff(1L);  // not qualified
        Staff staffB = makeStaff(2L);  // qualified

        Qualification qual = new Qualification();
        qual.setId(1L);
        qual.setName("RN");

        OffsetDateTime start = OffsetDateTime.of(2025, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        Shift shift = makeShift(1L, start, start.plusHours(8));

        ShiftQualificationRequirement req = new ShiftQualificationRequirement();
        req.setId(1L);
        req.setShift(shift);
        req.setQualification(qual);
        req.setMinimumCount(2);  // need 2 qualified, but only staffB has the qual

        StaffQualification staffBQual = new StaffQualification();
        staffBQual.setId(1L);
        staffBQual.setStaff(staffB);
        staffBQual.setQualification(qual);

        ShiftAssignment saA = assign(1L, shift, staffA);
        ShiftAssignment saB = assign(2L, shift, staffB);

        RuleConfiguration rc = ruleConfig(RuleType.MIN_QUALIFIED_STAFF_PER_SHIFT);

        // 1 qualified present, 2 required → shortfall of 1 → penalty of 1 hard
        verifier.verifyThat()
                .given(rc, req, staffBQual, saA, saB)
                .scores(HardMediumSoftScore.of(-1, 0, 0));
    }

    @Test
    void minQualifiedStaffPerShift_doesNotPenaliseWhenRequirementMet() {
        Staff staffA = makeStaff(1L);
        Staff staffB = makeStaff(2L);

        Qualification qual = new Qualification();
        qual.setId(1L);
        qual.setName("RN");

        OffsetDateTime start = OffsetDateTime.of(2025, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        Shift shift = makeShift(1L, start, start.plusHours(8));

        ShiftQualificationRequirement req = new ShiftQualificationRequirement();
        req.setId(1L);
        req.setShift(shift);
        req.setQualification(qual);
        req.setMinimumCount(1);  // need 1, both are qualified

        StaffQualification qualA = new StaffQualification();
        qualA.setId(1L);
        qualA.setStaff(staffA);
        qualA.setQualification(qual);

        StaffQualification qualB = new StaffQualification();
        qualB.setId(2L);
        qualB.setStaff(staffB);
        qualB.setQualification(qual);

        ShiftAssignment saA = assign(1L, shift, staffA);
        ShiftAssignment saB = assign(2L, shift, staffB);

        RuleConfiguration rc = ruleConfig(RuleType.MIN_QUALIFIED_STAFF_PER_SHIFT);

        verifier.verifyThat()
                .given(rc, req, qualA, qualB, saA, saB)
                .scores(HardMediumSoftScore.ZERO);
    }
}
