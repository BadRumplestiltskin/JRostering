package com.magicsystems.jrostering.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * The Timefold planning entity. Each row represents one staff slot on a {@link Shift}.
 *
 * <p>Slots are created automatically by {@code RosterService} when a {@code Shift} is saved,
 * with one slot per {@code minimumStaff}. The manager may add or remove unassigned,
 * non-pinned slots above the minimum before solving.</p>
 *
 * <p>The solver assigns a {@link Staff} value to each slot. Slots remain {@code null}
 * if the solver cannot fill them (infeasible result).</p>
 *
 * <p>{@code pinned = true} locks an assignment so the solver cannot change it.
 * Period 1 slots are pinned after publication before Period 2 is solved.</p>
 *
 * <p>This class carries both JPA persistence annotations and Timefold planning
 * annotations; they coexist without conflict.</p>
 */
@PlanningEntity
@Entity
@Table(name = "shift_assignment")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ShiftAssignment {

    @PlanningId
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    /**
     * The staff member assigned to this slot. {@code null} before solving or when
     * the solver could not fill the slot (infeasible).
     *
     * <p>This field is both the JPA FK and the Timefold planning variable.
     * The solver selects a value from the {@code staffRange} value range provider
     * defined in {@code RosterSolution}.</p>
     */
    @PlanningVariable(valueRangeProviderRefs = "staffRange", nullable = true)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    /**
     * When {@code true}, the solver cannot change this assignment.
     * Period 1 assignments are pinned after publication so that Period 2 solving
     * treats them as fixed problem facts.
     */
    @Column(nullable = false)
    private boolean pinned = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
