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
 * A date-range leave request for a {@link Staff} member.
 *
 * <p>Constraint applicability by status:</p>
 * <ul>
 *   <li>{@link LeaveStatus#APPROVED} — enforced by the {@code STAFF_LEAVE_BLOCK}
 *       hard constraint. The solver will never assign this staff member to a shift
 *       that overlaps the leave period.</li>
 *   <li>{@link LeaveStatus#REQUESTED} — considered by the {@code HONOUR_REQUESTED_LEAVE}
 *       soft constraint. The solver will avoid assigning the staff member but may
 *       do so when necessary.</li>
 *   <li>{@link LeaveStatus#REJECTED} — ignored by the solver entirely.</li>
 * </ul>
 */
@Entity
@Table(name = "leave")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Leave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 50)
    private LeaveType leaveType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private LeaveStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
