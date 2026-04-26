package com.magicsystems.jrostering.domain;

/**
 * Classifies the employment arrangement of a staff member.
 * Used to determine contracted-hours obligations and scheduling constraints.
 */
public enum EmploymentType {

    /** Staff member works a fixed number of contracted hours per week. */
    FULL_TIME,

    /** Staff member works fewer than full-time contracted hours per week. */
    PART_TIME,

    /** Staff member has no contracted hours and is rostered on an as-needed basis. */
    CASUAL
}
