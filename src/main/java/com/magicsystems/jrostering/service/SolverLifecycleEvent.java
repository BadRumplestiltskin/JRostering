package com.magicsystems.jrostering.service;

import com.magicsystems.jrostering.domain.RosterPeriod;
import com.magicsystems.jrostering.domain.SolverJob;

/**
 * Spring application events published by {@link NotificationService} for each
 * solver lifecycle outcome.
 *
 * <p>These events are published via {@link org.springframework.context.ApplicationEventPublisher}
 * so that future UI components (e.g. a Vaadin Server Push broadcaster) can subscribe
 * via {@link org.springframework.context.event.EventListener} without any change to
 * the service layer that produces them.</p>
 *
 * <p>All events are value-carrying records. Listeners receive a snapshot of the job
 * and period state as it was at the moment of publication — immutable for the
 * listener's purposes (the JPA entities are detached at that point).</p>
 */
public sealed interface SolverLifecycleEvent {

    /** Published when a solve run finishes with a fully feasible solution. */
    record Completed(SolverJob job, RosterPeriod period) implements SolverLifecycleEvent {}

    /** Published when the best solution found still violates hard or medium constraints. */
    record Infeasible(SolverJob job, RosterPeriod period) implements SolverLifecycleEvent {}

    /** Published when a manager cancels the running solve before the time limit expires. */
    record Cancelled(SolverJob job, RosterPeriod period) implements SolverLifecycleEvent {}

    /** Published when an unexpected exception terminates the solve run. */
    record Failed(SolverJob job, RosterPeriod period, Throwable cause) implements SolverLifecycleEvent {}
}
