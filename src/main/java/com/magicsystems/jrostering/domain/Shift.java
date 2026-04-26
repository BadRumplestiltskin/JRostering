package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * An individual shift instance within a {@link RosterPeriod}.
 *
 * <p>When a {@code Shift} is saved, {@code RosterService} automatically creates
 * {@code minimumStaff} {@link ShiftAssignment} slot rows with {@code staff = null}.
 * Slot count is reconciled when {@code minimumStaff} changes — slots may only be
 * removed if they are unassigned and not pinned.</p>
 *
 * <p>{@code shiftType} is optional. Assigning a {@link ShiftType} enables the
 * {@code PREFERRED_SHIFT_TYPE} and {@code AVOID_SHIFT_TYPE} soft constraint rules
 * to evaluate staff preferences for this shift.</p>
 *
 * <p>The database enforces: {@code end_datetime > start_datetime}.</p>
 */
@Entity
@Table(name = "shift")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roster_period_id", nullable = false)
    private RosterPeriod rosterPeriod;

    /**
     * Optional classification of this shift (e.g. Morning, Night).
     * Required for {@code PREFERRED_SHIFT_TYPE} / {@code AVOID_SHIFT_TYPE} constraint evaluation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_type_id")
    private ShiftType shiftType;

    /** Optional human-readable label (e.g. "Morning Shift"). */
    @Column(length = 255)
    private String name;

    @NotNull
    @Column(name = "start_datetime", nullable = false)
    private OffsetDateTime startDatetime;

    @NotNull
    @Column(name = "end_datetime", nullable = false)
    private OffsetDateTime endDatetime;

    /**
     * Minimum number of staff that must be assigned to this shift.
     * Drives the number of {@link ShiftAssignment} slots created by {@code RosterService}.
     */
    @Min(1)
    @Column(name = "minimum_staff", nullable = false)
    private int minimumStaff = 1;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
