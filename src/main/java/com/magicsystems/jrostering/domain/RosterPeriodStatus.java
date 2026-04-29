package com.magicsystems.jrostering.domain;

/**
 * Lifecycle status of a {@link RosterPeriod}.
 *
 * <h3>Transition map</h3>
 * <pre>
 * DRAFT      → SOLVING  (submitSolve)
 *            → CANCELLED
 *
 * SOLVING    → SOLVED     (solver completed feasibly)
 *            → INFEASIBLE (solver completed with hard/medium violations)
 *            → DRAFT      (solve cancelled or failed — reverted)
 *
 * SOLVED     → PUBLISHED (publish)
 *            → DRAFT     (revertToDraft)
 *            → CANCELLED
 *
 * PUBLISHED  → DRAFT     (revertToDraft)
 *            → CANCELLED
 *
 * INFEASIBLE → SOLVING   (re-submit)
 *            → DRAFT     (revertToDraft)
 *            → CANCELLED
 *
 * CANCELLED  → (terminal — no transitions)
 * </pre>
 *
 * <p>Predicate methods centralise transition guards so that callers do not
 * scatter raw {@code ==} comparisons or {@code Set.of()} lookups across the
 * service layer. The exhaustive {@code switch} in each predicate is a compile
 * error when a new status value is added without updating the guard.</p>
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
     * The solver returned a solution with negative hard or medium score.
     * The best partial solution found is preserved in {@link ShiftAssignment} rows;
     * unresolvable slots remain with {@code staff = null}.
     */
    INFEASIBLE,

    /** The roster period has been cancelled. */
    CANCELLED;

    // =========================================================================
    // Transition predicates
    // =========================================================================

    /** Whether a solve may be submitted for this period (DRAFT or INFEASIBLE). */
    public boolean isSolvable() {
        return switch (this) {
            case DRAFT, INFEASIBLE -> true;
            case SOLVING, SOLVED, PUBLISHED, CANCELLED -> false;
        };
    }

    /** Whether this period may be reverted to DRAFT by the manager (SOLVED, PUBLISHED, or INFEASIBLE). */
    public boolean isRevertable() {
        return switch (this) {
            case SOLVED, PUBLISHED, INFEASIBLE -> true;
            case DRAFT, SOLVING, CANCELLED -> false;
        };
    }

    /** Whether this period may be published (SOLVED only). */
    public boolean isPublishable() {
        return switch (this) {
            case SOLVED -> true;
            case DRAFT, SOLVING, PUBLISHED, INFEASIBLE, CANCELLED -> false;
        };
    }

    /**
     * Whether this period may be cancelled (anything except SOLVING or already CANCELLED).
     * A SOLVING period must be cancelled via the solver job instead.
     */
    public boolean isCancellable() {
        return switch (this) {
            case DRAFT, SOLVED, PUBLISHED, INFEASIBLE -> true;
            case SOLVING, CANCELLED -> false;
        };
    }

    /**
     * Whether shifts on this period may be modified (DRAFT, SOLVED, PUBLISHED, INFEASIBLE).
     * Modifications on non-DRAFT periods trigger an automatic revert to DRAFT.
     */
    public boolean isModifiable() {
        return switch (this) {
            case DRAFT, SOLVED, PUBLISHED, INFEASIBLE -> true;
            case SOLVING, CANCELLED -> false;
        };
    }

    /**
     * Whether a following (period 2) roster period may be created after this one
     * (SOLVED or PUBLISHED).
     */
    public boolean allowsFollowingPeriod() {
        return switch (this) {
            case SOLVED, PUBLISHED -> true;
            case DRAFT, SOLVING, INFEASIBLE, CANCELLED -> false;
        };
    }
}
