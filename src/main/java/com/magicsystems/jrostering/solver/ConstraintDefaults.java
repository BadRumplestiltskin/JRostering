package com.magicsystems.jrostering.solver;

/**
 * Named constants for constraint parameter key strings and their default values.
 *
 * <p>Parameter keys are the JSON property names stored in
 * {@link com.magicsystems.jrostering.domain.RuleConfiguration#parameterJson}.
 * Default values match the values seeded by
 * {@link com.magicsystems.jrostering.service.SiteService} at site-creation time
 * and serve as fallbacks when the DB row carries no explicit value for the key.</p>
 *
 * <p>All members are package-private; they are consumed only by
 * {@link RosterConstraintProvider} within this package.</p>
 */
final class ConstraintDefaults {

    private ConstraintDefaults() {}

    // -------------------------------------------------------------------------
    // Constraint name — cross-site blocking (not gated by RuleConfiguration)
    // -------------------------------------------------------------------------

    static final String CROSS_SITE_BLOCKING        = "CROSS_SITE_BLOCKING";

    // -------------------------------------------------------------------------
    // Parameter key strings
    // -------------------------------------------------------------------------

    static final String KEY_MINIMUM_REST_HOURS     = "minimumRestHours";
    static final String KEY_MAXIMUM_HOURS          = "maximumHours";
    static final String KEY_MAXIMUM_DAYS           = "maximumDays";
    static final String KEY_PENALTY_PER_VIOLATION  = "penaltyPerViolation";
    static final String KEY_MAX_DEVIATION_HOURS    = "maximumDeviationHours";
    static final String KEY_MAX_DEVIATION_SHIFTS   = "maximumDeviationShifts";
    static final String KEY_PENALTY_MISSING_STAFF  = "penaltyPerMissingStaff";
    static final String KEY_PENALTY_EXTRA_STAFF    = "penaltyPerExtraStaff";
    static final String KEY_PREFERRED_MAX_DAYS     = "preferredMaxDays";
    static final String KEY_PENALTY_EXTRA_DAY      = "penaltyPerExtraDay";
    static final String KEY_PENALTY_EXTRA_HOUR     = "penaltyPerExtraHour";

    // -------------------------------------------------------------------------
    // Default parameter values
    // -------------------------------------------------------------------------

    static final int DEFAULT_MINIMUM_REST_HOURS     = 10;
    /** Default for MAX_HOURS_PER_DAY (key: {@link #KEY_MAXIMUM_HOURS}). */
    static final int DEFAULT_MAX_HOURS_PER_DAY      = 12;
    /** Default for MAX_HOURS_PER_WEEK (key: {@link #KEY_MAXIMUM_HOURS}). */
    static final int DEFAULT_MAX_HOURS_PER_WEEK     = 38;
    /** Default for SOFT_MAX_HOURS_PER_PERIOD (key: {@link #KEY_MAXIMUM_HOURS}). */
    static final int DEFAULT_MAX_HOURS_PER_PERIOD   = 76;
    static final int DEFAULT_MAX_CONSECUTIVE_DAYS   = 5;
    static final int DEFAULT_PENALTY_PER_VIOLATION  = 1;
    /** Default for HONOUR_REQUESTED_LEAVE penalty (key: {@link #KEY_PENALTY_PER_VIOLATION}). */
    static final int DEFAULT_PENALTY_REQUESTED_LEAVE = 5;
    static final int DEFAULT_MAX_DEVIATION_HOURS    = 4;
    static final int DEFAULT_MAX_DEVIATION_SHIFTS   = 2;
    static final int DEFAULT_PENALTY_MISSING_STAFF  = 10;
    static final int DEFAULT_PENALTY_EXTRA_STAFF    = 1;
    static final int DEFAULT_PREFERRED_MAX_DAYS     = 4;
    static final int DEFAULT_PENALTY_EXTRA_DAY      = 2;
    static final int DEFAULT_PENALTY_EXTRA_HOUR     = 3;

    // -------------------------------------------------------------------------
    // Default weight fallbacks (used in RuleConfiguration.weightOrDefault)
    // -------------------------------------------------------------------------

    /** Generic fallback for rules seeded as HARD that a site reconfigures to SOFT. */
    static final int DEFAULT_WEIGHT                 = 1;
    /** Fallback weight for FAIR_HOURS_DISTRIBUTION and SOFT_MAX_HOURS_PER_PERIOD. */
    static final int DEFAULT_FAIR_HOURS_WEIGHT      = 3;
    /** Fallback weight for FAIR_WEEKEND/NIGHT_DISTRIBUTION and AVOID_EXCESSIVE_CONSECUTIVE_DAYS. */
    static final int DEFAULT_FAIR_SHIFT_WEIGHT      = 2;
}
