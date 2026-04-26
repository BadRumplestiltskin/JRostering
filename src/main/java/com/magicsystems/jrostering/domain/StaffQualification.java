package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Records that a {@link Staff} member holds a given {@link Qualification}.
 *
 * <p>Used by the solver when evaluating {@code QUALIFICATION_REQUIRED_FOR_SHIFT}
 * and {@code MIN_QUALIFIED_STAFF_PER_SHIFT} constraints.</p>
 *
 * <p>A staff member may not hold the same qualification twice (unique per staff + qualification).</p>
 */
@Entity
@Table(
    name = "staff_qualification",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_staff_qualification",
        columnNames = {"staff_id", "qualification_id"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class StaffQualification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qualification_id", nullable = false)
    private Qualification qualification;

    /** Date the qualification was awarded. May be {@code null} if not recorded. */
    @Column(name = "awarded_date")
    private LocalDate awardedDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
