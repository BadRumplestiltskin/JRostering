package com.magicsystems.jrostering.domain;

/**
 * Determines which component of the {@code HardMediumSoftScore} a
 * {@link RuleConfiguration} contributes to.
 *
 * <ul>
 *   <li>{@code HARD} — constraint must never be violated in a valid roster.</li>
 *   <li>{@code MEDIUM} — treated as near-absolute; violated only when no feasible
 *       solution exists (e.g. {@code STAFF_AVAILABILITY_BLOCK} on advisory sites).</li>
 *   <li>{@code SOFT} — weighted preference optimisation.</li>
 * </ul>
 */
public enum ConstraintLevel {
    HARD,
    MEDIUM,
    SOFT
}
