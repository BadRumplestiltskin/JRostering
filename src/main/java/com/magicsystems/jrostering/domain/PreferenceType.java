package com.magicsystems.jrostering.domain;

/**
 * Classifies a staff member's scheduling preference stored in {@link StaffPreference}.
 *
 * <ul>
 *   <li>{@code PREFERRED_DAY_OFF} — requires {@code dayOfWeek} to be set.</li>
 *   <li>{@code PREFERRED_SHIFT_TYPE} — requires {@code shiftType} to be set.</li>
 *   <li>{@code AVOID_SHIFT_TYPE} — requires {@code shiftType} to be set.</li>
 * </ul>
 */
public enum PreferenceType {

    /** Staff member prefers not to work on a given day of the week. */
    PREFERRED_DAY_OFF,

    /** Staff member prefers to be assigned to a given shift type. */
    PREFERRED_SHIFT_TYPE,

    /** Staff member prefers not to be assigned to a given shift type. */
    AVOID_SHIFT_TYPE
}
