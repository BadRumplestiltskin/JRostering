package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Records that two staff members must always be assigned to the same shift together.
 * Evaluated as a hard constraint by the solver when {@code STAFF_MUST_PAIR} is enabled.
 *
 * <p>Canonical ordering is enforced at the database level:
 * {@code staffA.id < staffB.id}. Application code must enforce this ordering
 * when creating records.</p>
 *
 * <p>Each pair is unique.</p>
 */
@Entity
@Table(
    name = "staff_pairing",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_staff_pairing",
        columnNames = {"staff_a_id", "staff_b_id"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class StaffPairing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The staff member with the lower database ID in the required pair.
     * Must satisfy: {@code staffA.id < staffB.id}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_a_id", nullable = false)
    private Staff staffA;

    /**
     * The staff member with the higher database ID in the required pair.
     * Must satisfy: {@code staffA.id < staffB.id}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_b_id", nullable = false)
    private Staff staffB;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
