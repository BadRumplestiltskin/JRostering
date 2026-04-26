package com.magicsystems.jrostering.domain;

/**
 * Identifies each configurable scheduling rule supported by the constraint engine.
 *
 * <p>Each value maps to one {@link RuleConfiguration} row per site, which stores
 * the constraint level (HARD / MEDIUM / SOFT), enabled flag, optional weight,
 * and rule-specific parameters as JSONB.</p>
 *
 * <h3>Hard Rules (default constraint_level = HARD)</h3>
 * <ul>
 *   <li>{@code MIN_REST_BETWEEN_SHIFTS} — minimum rest hours between consecutive shifts.</li>
 *   <li>{@code MAX_HOURS_PER_DAY} — maximum hours a staff member may work in one day.</li>
 *   <li>{@code MAX_HOURS_PER_WEEK} — maximum hours a staff member may work in one week.</li>
 *   <li>{@code MAX_CONSECUTIVE_DAYS} — maximum consecutive working days.</li>
 *   <li>{@code MIN_STAFF_PER_SHIFT} — minimum total staff on a shift (from Shift.minimumStaff).</li>
 *   <li>{@code MIN_QUALIFIED_STAFF_PER_SHIFT} — minimum qualified staff per shift requirement.</li>
 *   <li>{@code QUALIFICATION_REQUIRED_FOR_SHIFT} — staff assigned to a shift must hold required qualifications.</li>
 *   <li>{@code STAFF_MUTUAL_EXCLUSION} — staff pairs in STAFF_INCOMPATIBILITY must never share a shift.</li>
 *   <li>{@code STAFF_MUST_PAIR} — staff pairs in STAFF_PAIRING must always share a shift.</li>
 *   <li>{@code STAFF_LEAVE_BLOCK} — staff with APPROVED leave cannot be assigned. Always hard.</li>
 *   <li>{@code STAFF_AVAILABILITY_BLOCK} — staff cannot be assigned outside declared availability windows.
 *       May be set to MEDIUM per site for advisory availability.</li>
 * </ul>
 *
 * <h3>Soft Rules (default constraint_level = SOFT)</h3>
 * <ul>
 *   <li>{@code PREFERRED_DAYS_OFF} — honour PREFERRED_DAY_OFF preferences.</li>
 *   <li>{@code PREFERRED_SHIFT_TYPE} — honour PREFERRED_SHIFT_TYPE and AVOID_SHIFT_TYPE preferences.</li>
 *   <li>{@code HONOUR_REQUESTED_LEAVE} — honour REQUESTED (not yet approved) leave.</li>
 *   <li>{@code FAIR_HOURS_DISTRIBUTION} — minimise deviation in hours worked across staff.</li>
 *   <li>{@code FAIR_WEEKEND_DISTRIBUTION} — minimise deviation in weekend shifts across staff.</li>
 *   <li>{@code FAIR_NIGHT_SHIFT_DISTRIBUTION} — minimise deviation in night shifts across staff.</li>
 *   <li>{@code MINIMISE_UNDERSTAFFING} — penalise shifts with fewer staff than minimum.</li>
 *   <li>{@code MINIMISE_OVERSTAFFING} — penalise shifts with more staff than needed.</li>
 *   <li>{@code AVOID_EXCESSIVE_CONSECUTIVE_DAYS} — penalise runs exceeding preferred max days.</li>
 *   <li>{@code SOFT_MAX_HOURS_PER_PERIOD} — penalise staff exceeding preferred period hours.</li>
 * </ul>
 */
public enum RuleType {

    // ---- Hard rules -------------------------------------------------------

    MIN_REST_BETWEEN_SHIFTS,
    MAX_HOURS_PER_DAY,
    MAX_HOURS_PER_WEEK,
    MAX_CONSECUTIVE_DAYS,
    MIN_STAFF_PER_SHIFT,
    MIN_QUALIFIED_STAFF_PER_SHIFT,
    QUALIFICATION_REQUIRED_FOR_SHIFT,
    STAFF_MUTUAL_EXCLUSION,
    STAFF_MUST_PAIR,
    STAFF_LEAVE_BLOCK,
    STAFF_AVAILABILITY_BLOCK,

    // ---- Soft rules -------------------------------------------------------

    PREFERRED_DAYS_OFF,
    PREFERRED_SHIFT_TYPE,
    HONOUR_REQUESTED_LEAVE,
    FAIR_HOURS_DISTRIBUTION,
    FAIR_WEEKEND_DISTRIBUTION,
    FAIR_NIGHT_SHIFT_DISTRIBUTION,
    MINIMISE_UNDERSTAFFING,
    MINIMISE_OVERSTAFFING,
    AVOID_EXCESSIVE_CONSECUTIVE_DAYS,
    SOFT_MAX_HOURS_PER_PERIOD
}
