package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * A recurring weekly availability window declared by a {@link Staff} member.
 *
 * <p>When the {@code STAFF_AVAILABILITY_BLOCK} rule is enabled and configured as
 * {@link ConstraintLevel#HARD}, the solver will not assign staff to shifts that
 * fall outside their declared availability. When configured as
 * {@link ConstraintLevel#MEDIUM}, availability is treated as near-absolute and
 * may be overridden only when no feasible solution exists.</p>
 *
 * <p>Setting {@code available = false} marks the staff member as unavailable
 * during the specified window.</p>
 */
@Entity
@Table(name = "staff_availability")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class StaffAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 20)
    private DayOfWeek dayOfWeek;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** {@code true} if the staff member is available; {@code false} if unavailable. */
    @Column(nullable = false)
    private boolean available = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
