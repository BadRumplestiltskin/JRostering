package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A 14-day planning period (one fortnight) for a {@link Site}.
 *
 * <p>A maximum of two sequential periods may be active at once (28 days total).
 * The relationship between sequential periods is modelled via {@code previousPeriod}
 * — a self-referential nullable FK. This FK is the authoritative link; {@code sequenceNumber}
 * (1 or 2) is a display convenience only.</p>
 *
 * <p>The database enforces: {@code end_date = start_date + 13 days}.</p>
 *
 * <p>Status transitions:</p>
 * <pre>
 * DRAFT → SOLVING → SOLVED → PUBLISHED
 *                 → INFEASIBLE  (partial solution preserved in ShiftAssignment rows)
 *       → CANCELLED
 * </pre>
 */
@Entity
@Table(name = "roster_period")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class RosterPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    /**
     * The preceding roster period in this sequence. {@code null} for the first period.
     * This FK is the authoritative link between sequential periods.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_period_id")
    private RosterPeriod previousPeriod;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Always {@code startDate + 13 days}. Enforced at the database level by a CHECK constraint.
     */
    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RosterPeriodStatus status = RosterPeriodStatus.DRAFT;

    /**
     * Display convenience: 1 for the first period in the sequence, 2 for the second.
     * Derived from the {@code previousPeriod} FK chain and must not be used as the
     * authoritative sequence relationship.
     */
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    /** Populated when {@code status} transitions to {@link RosterPeriodStatus#PUBLISHED}. */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
