package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;

/**
 * A scheduling preference declared by a {@link Staff} member.
 * Used by the {@code PREFERRED_DAYS_OFF} and {@code PREFERRED_SHIFT_TYPE} soft rules.
 *
 * <p>Exactly one of {@code dayOfWeek} or {@code shiftType} must be set,
 * depending on {@code preferenceType}:</p>
 * <ul>
 *   <li>{@link PreferenceType#PREFERRED_DAY_OFF} — {@code dayOfWeek} must be set;
 *       {@code shiftType} must be {@code null}.</li>
 *   <li>{@link PreferenceType#PREFERRED_SHIFT_TYPE} or
 *       {@link PreferenceType#AVOID_SHIFT_TYPE} — {@code shiftType} must be set;
 *       {@code dayOfWeek} must be {@code null}.</li>
 * </ul>
 *
 * <p>This constraint is enforced at the database level via a CHECK constraint
 * in the Flyway migration.</p>
 */
@Entity
@Table(name = "staff_preference")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class StaffPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "preference_type", nullable = false, length = 50)
    private PreferenceType preferenceType;

    /**
     * Populated for {@link PreferenceType#PREFERRED_DAY_OFF}; {@code null} otherwise.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 20)
    private DayOfWeek dayOfWeek;

    /**
     * Populated for {@link PreferenceType#PREFERRED_SHIFT_TYPE} and
     * {@link PreferenceType#AVOID_SHIFT_TYPE}; {@code null} otherwise.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_type_id")
    private ShiftType shiftType;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
