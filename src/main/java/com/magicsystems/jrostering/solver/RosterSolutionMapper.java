package com.magicsystems.jrostering.solver;

import com.magicsystems.jrostering.domain.*;
import com.magicsystems.jrostering.repository.*;
import com.magicsystems.jrostering.service.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a {@link RosterSolution} from persistent domain objects for a given
 * {@link RosterPeriod} and writes solver results back to the database.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Loading all problem facts required by {@link RosterConstraintProvider}.</li>
 *   <li>Constructing {@link CrossSiteBlockingPeriod} records for multi-site staff
 *       members who hold confirmed commitments at other sites during the planning window.</li>
 *   <li>Persisting the best solution produced by the solver back to
 *       {@link ShiftAssignment} rows after a solve completes.</li>
 * </ul>
 *
 * <h3>Query strategy</h3>
 * <p>All collections are loaded in batch to avoid N+1 queries. The staff list is
 * fetched once via {@code StaffSiteAssignmentRepository.findBySite}, and all
 * per-staff collections (qualifications, preferences, availabilities, leave,
 * incompatibilities, pairings) are fetched with {@code IN} queries over that set.</p>
 *
 * <h3>Transaction scope</h3>
 * <p>{@code buildSolution} runs in a read-only transaction; {@code persistSolution}
 * runs in a write transaction. Both are called from {@code SolverService}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RosterSolutionMapper {

    private final RosterPeriodRepository                rosterPeriodRepository;
    private final ShiftRepository                       shiftRepository;
    private final ShiftAssignmentRepository             shiftAssignmentRepository;
    private final ShiftQualificationRequirementRepository shiftQualReqRepository;
    private final StaffSiteAssignmentRepository         staffSiteAssignmentRepository;
    private final StaffQualificationRepository          staffQualificationRepository;
    private final StaffPreferenceRepository             staffPreferenceRepository;
    private final StaffAvailabilityRepository           staffAvailabilityRepository;
    private final StaffIncompatibilityRepository        staffIncompatibilityRepository;
    private final StaffPairingRepository                staffPairingRepository;
    private final LeaveRepository                       leaveRepository;
    private final RuleConfigurationRepository           ruleConfigurationRepository;

    // =========================================================================
    // Build
    // =========================================================================

    /**
     * Loads all domain objects for the given roster period and assembles them
     * into a {@link RosterSolution} ready for submission to the Timefold solver.
     *
     * <p>The period is loaded fresh from the database inside this method so that
     * all lazy JPA associations (particularly {@code period.getSite()}) are
     * available within this transaction. Callers must <em>not</em> pass a detached
     * entity — pass the period ID instead.</p>
     *
     * @param rosterPeriodId the ID of the roster period to solve
     * @return a fully-populated {@link RosterSolution}
     * @throws EntityNotFoundException if no period with the given ID exists
     */
    @Transactional(readOnly = true)
    public RosterSolution buildSolution(Long rosterPeriodId) {
        RosterPeriod period = rosterPeriodRepository.findById(rosterPeriodId)
                .orElseThrow(() -> EntityNotFoundException.of("RosterPeriod", rosterPeriodId));

        log.debug("Building RosterSolution for rosterPeriodId={} siteId={}",
                period.getId(), period.getSite().getId());

        // ── Shifts and assignments ─────────────────────────────────────────────
        List<Shift> shifts = shiftRepository.findByRosterPeriodOrderByStartDatetimeAsc(period);
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByRosterPeriod(period);

        // ── Shift qualification requirements (batch) ──────────────────────────
        List<ShiftQualificationRequirement> shiftQualReqs =
                shiftQualReqRepository.findByShiftIn(shifts);

        // Derive planning window in OffsetDateTime for cross-site query
        OffsetDateTime windowStart = period.getStartDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime windowEnd   = period.getEndDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        // ── Eligible staff (active only, filtered in SQL) ──────────────────────
        List<StaffSiteAssignment> siteAssignments =
                staffSiteAssignmentRepository.findBySiteAndStaffActiveTrue(period.getSite());

        List<Staff> eligibleStaff = siteAssignments.stream()
                .map(StaffSiteAssignment::getStaff)
                .toList();

        log.debug("  eligible staff count={}", eligibleStaff.size());

        // ── Per-staff collections (all batched) ────────────────────────────────
        List<StaffQualification> staffQualifications =
                staffQualificationRepository.findByStaffIn(eligibleStaff);

        List<StaffPreference> staffPreferences =
                staffPreferenceRepository.findByStaffIn(eligibleStaff);

        List<StaffAvailability> staffAvailabilities =
                staffAvailabilityRepository.findByStaffIn(eligibleStaff);

        List<StaffIncompatibility> staffIncompatibilities =
                staffIncompatibilityRepository.findByStaffAInOrStaffBIn(eligibleStaff);

        List<StaffPairing> staffPairings =
                staffPairingRepository.findByStaffAInOrStaffBIn(eligibleStaff);

        // ── Leave — APPROVED and REQUESTED, within the planning window ─────────
        List<Leave> leaveRecords = leaveRepository.findByStaffInAndStatusInAndDateRange(
                eligibleStaff,
                LeaveStatus.APPROVED,
                LeaveStatus.REQUESTED,
                period.getStartDate(),
                period.getEndDate()
        );

        // ── Rule configurations (enabled only) ────────────────────────────────
        List<RuleConfiguration> ruleConfigurations =
                ruleConfigurationRepository.findBySiteAndEnabledTrue(period.getSite());

        // ── Cross-site blocking periods ────────────────────────────────────────
        List<CrossSiteBlockingPeriod> crossSiteBlockingPeriods =
                buildCrossSiteBlockingPeriods(eligibleStaff, period.getSite().getId(),
                        windowStart, windowEnd);

        log.debug("  cross-site blocking periods count={}", crossSiteBlockingPeriods.size());

        // ── Derive unique ShiftType and Qualification collections ──────────────
        // These are extracted from the already-loaded objects; no extra queries needed.
        List<ShiftType> shiftTypes = shifts.stream()
                .map(Shift::getShiftType)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<Qualification> qualifications = shiftQualReqs.stream()
                .map(ShiftQualificationRequirement::getQualification)
                .distinct()
                .toList();

        // ── Assemble solution ──────────────────────────────────────────────────
        RosterSolution solution = new RosterSolution();
        solution.setShifts(shifts);
        solution.setShiftAssignments(assignments);
        solution.setShiftQualificationRequirements(shiftQualReqs);
        solution.setStaff(eligibleStaff);
        solution.setShiftTypes(shiftTypes);
        solution.setQualifications(qualifications);
        solution.setStaffQualifications(staffQualifications);
        solution.setStaffPreferences(staffPreferences);
        solution.setStaffAvailabilities(staffAvailabilities);
        solution.setStaffIncompatibilities(staffIncompatibilities);
        solution.setStaffPairings(staffPairings);
        solution.setLeaveRecords(leaveRecords);
        solution.setRuleConfigurations(ruleConfigurations);
        solution.setCrossSiteBlockingPeriods(crossSiteBlockingPeriods);

        log.debug("RosterSolution built: shifts={} assignments={} staff={} rules={}",
                shifts.size(), assignments.size(), eligibleStaff.size(),
                ruleConfigurations.size());

        return solution;
    }

    // =========================================================================
    // Persist
    // =========================================================================

    /**
     * Writes the staff assignments from the solver's best solution back to the database.
     *
     * <p>Each {@link ShiftAssignment} in the solved solution has its {@code staff} field
     * updated to reflect the solver's decision. Unassigned slots (infeasible) retain
     * {@code staff = null}.</p>
     *
     * <p>Pinned assignments are included in the save call but their {@code staff} field
     * has not been modified by the solver — the value written is identical to the value
     * that was loaded, so the update is a no-op for pinned rows.</p>
     *
     * @param solution the solution returned by the Timefold solver
     */
    @Transactional
    public void persistSolution(RosterSolution solution) {
        List<ShiftAssignment> solved = solution.getShiftAssignments();
        if (solved.isEmpty()) {
            return;
        }
        // Build a map of id → staff from the solver's (detached) output.
        Map<Long, Staff> staffById = new HashMap<>(solved.size());
        for (ShiftAssignment sa : solved) {
            staffById.put(sa.getId(), sa.getStaff());
        }
        // Load managed entities in a single IN query, then set staff via dirty-check.
        // This replaces saveAll(detached) which issued one SELECT+UPDATE per row.
        List<Long> ids = solved.stream().map(ShiftAssignment::getId).toList();
        List<ShiftAssignment> managed = shiftAssignmentRepository.findAllById(ids);
        managed.forEach(sa -> sa.setStaff(staffById.get(sa.getId())));
        log.debug("Persisted {} ShiftAssignment rows", managed.size());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Builds {@link CrossSiteBlockingPeriod} instances for all eligible staff members
     * who hold a confirmed shift at a different site during the planning window.
     *
     * <p>A single batch query is issued for the entire staff pool, replacing the previous
     * per-staff N+1 loop. Only assignments in {@link RosterPeriodStatus#PUBLISHED} or
     * {@link RosterPeriodStatus#SOLVED} periods at other sites are included — enforced
     * by the JPQL in
     * {@link ShiftAssignmentRepository#findCrossSiteAssignmentsByStaffIn}.</p>
     *
     * <p>If {@code eligibleStaff} is empty, the query is skipped entirely and an empty
     * list is returned.</p>
     *
     * @param eligibleStaff the full set of staff eligible for the period being solved
     * @param siteId        the ID of the site being solved (excluded from results)
     * @param windowStart   start of the planning window (inclusive)
     * @param windowEnd     exclusive end of the planning window (end_date + 1 day)
     * @return blocking period records to inject as problem facts
     */
    private List<CrossSiteBlockingPeriod> buildCrossSiteBlockingPeriods(
            List<Staff> eligibleStaff,
            Long siteId,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd) {

        if (eligibleStaff.isEmpty()) {
            return List.of();
        }

        List<ShiftAssignment> crossSiteAssignments = shiftAssignmentRepository
                .findCrossSiteAssignmentsByStaffIn(eligibleStaff, siteId, windowStart, windowEnd);

        List<CrossSiteBlockingPeriod> result = new ArrayList<>(crossSiteAssignments.size());
        for (ShiftAssignment sa : crossSiteAssignments) {
            result.add(new CrossSiteBlockingPeriod(
                    sa.getStaff(),
                    sa.getShift().getStartDatetime(),
                    sa.getShift().getEndDatetime()
            ));
        }
        return result;
    }
}
