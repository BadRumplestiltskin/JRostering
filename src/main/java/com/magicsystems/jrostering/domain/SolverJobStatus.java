package com.magicsystems.jrostering.domain;

/**
 * Lifecycle status of an asynchronous solver run tracked by {@link SolverJob}.
 */
public enum SolverJobStatus {

    /** Job has been submitted but the background thread has not yet started it. */
    QUEUED,

    /** Solver is actively running. */
    RUNNING,

    /** Solver finished with a feasible solution (zero hard score). */
    COMPLETED,

    /** Manager cancelled the solve; the best solution found so far was preserved. */
    CANCELLED,

    /**
     * Solver finished but could not achieve a feasible solution.
     * The best partial solution is preserved in {@link ShiftAssignment} rows.
     */
    INFEASIBLE,

    /**
     * An unexpected error terminated the solve, or the job was orphaned by an
     * application restart and recovered by {@code StartupRecoveryService}.
     */
    FAILED
}
