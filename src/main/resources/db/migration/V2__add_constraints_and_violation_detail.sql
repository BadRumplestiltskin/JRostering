-- =============================================================================
-- V2__add_constraints_and_violation_detail.sql
-- Adds data-integrity constraints identified during architecture review:
--   1. Partial unique indices on STAFF_PREFERENCE (prevents duplicate entries)
--   2. CHECK constraint on STAFF_AVAILABILITY (no overnight-crossing windows)
--   3. JSONB column on SOLVER_JOB for per-constraint violation detail
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. STAFF_PREFERENCE — unique indices
--
-- The CHECK constraint in V1 enforces that exactly one of day_of_week /
-- shift_type_id is set.  These partial unique indices prevent a staff member
-- from having duplicate preference rows of the same type for the same value.
--
-- Standard UNIQUE constraints cannot enforce this because the NULL column in
-- each row pattern would be excluded from a conventional unique index.  Partial
-- indices are the correct PostgreSQL mechanism.
-- -----------------------------------------------------------------------------

-- Prevents duplicate PREFERRED_DAY_OFF rows for the same staff member and day.
CREATE UNIQUE INDEX uq_staff_pref_day_off
    ON staff_preference (staff_id, day_of_week)
    WHERE preference_type = 'PREFERRED_DAY_OFF';

-- Prevents duplicate PREFERRED_SHIFT_TYPE or AVOID_SHIFT_TYPE rows for the
-- same staff member, preference type, and shift type.
CREATE UNIQUE INDEX uq_staff_pref_shift_type
    ON staff_preference (staff_id, preference_type, shift_type_id)
    WHERE preference_type IN ('PREFERRED_SHIFT_TYPE', 'AVOID_SHIFT_TYPE');

-- -----------------------------------------------------------------------------
-- 2. STAFF_AVAILABILITY — end_time must be strictly after start_time
--
-- Overnight availability windows crossing midnight (e.g. 22:00–06:00) are not
-- supported in the initial version.  The TIME type cannot represent such a
-- range unambiguously without a date component.  This constraint makes the
-- limitation explicit and prevents silent constraint-provider errors.
-- -----------------------------------------------------------------------------
ALTER TABLE staff_availability
    ADD CONSTRAINT chk_staff_availability_times CHECK (end_time > start_time);

-- -----------------------------------------------------------------------------
-- 3. SOLVER_JOB — per-constraint violation detail
--
-- Populated by SolverTransactionHelper after each terminal solve state
-- (COMPLETED, INFEASIBLE, CANCELLED).  Stores a JSON array of constraint match
-- totals serialised by SolverExecutor via Timefold's ScoreManager API.
-- Used by the Rule Violation Summary report.
--
-- Example value:
--   [{"constraintName":"MIN_STAFF_PER_SHIFT","score":"0hard/0medium/-3soft","violations":3},
--    {"constraintName":"PREFERRED_DAYS_OFF","score":"0hard/0medium/-2soft","violations":2}]
-- -----------------------------------------------------------------------------
ALTER TABLE solver_job
    ADD COLUMN violation_detail_json JSONB;
