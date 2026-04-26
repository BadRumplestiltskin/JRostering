package com.magicsystems.jrostering.domain;

/**
 * Lifecycle status of a leave request.
 *
 * <p>The {@code STAFF_LEAVE_BLOCK} hard constraint applies only to {@code APPROVED} leave.
 * The {@code HONOUR_REQUESTED_LEAVE} soft constraint applies only to {@code REQUESTED} leave.</p>
 */
public enum LeaveStatus {

    /** Leave has been submitted but not yet reviewed. */
    REQUESTED,

    /** Leave has been approved; the solver treats it as an absolute hard block. */
    APPROVED,

    /** Leave request was declined. */
    REJECTED
}
