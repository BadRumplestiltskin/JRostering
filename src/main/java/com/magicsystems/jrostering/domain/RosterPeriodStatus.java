package com.magicsystems.jrostering.domain;

/**
 * Lifecycle status of a {@link RosterPeriod}.
 */
public enum RosterPeriodStatus {

    /** Roster period has been created; shifts may be edited freely. */
    DRAFT,

    /** A solver job is currently running for this period. */
    SOLVING,

    /** The solver completed successfully; roster is ready for review. */
    SOLVED,

    /** The roster has been published and is visible to managers. */
    PUBLISHED,

    /**
     * The solver returned a solution with negative hard score.
     * The best partial solution found is preserved in {@link ShiftAssignment} rows;
     * unresolvable slots remain with {@code staff = null}.
     */
    INFEASIBLE,

    /** The roster period has been cancelled. */
    CANCELLED
}
