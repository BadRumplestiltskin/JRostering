package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Reference data classifying shifts by type within a {@link Site}
 * (e.g. Morning, Afternoon, Night).
 *
 * <p>A {@link Shift} may optionally reference a {@code ShiftType}, enabling the
 * {@code PREFERRED_SHIFT_TYPE} and {@code AVOID_SHIFT_TYPE} soft constraints to
 * evaluate {@link StaffPreference} records against a stable classification rather
 * than free-text shift names.</p>
 *
 * <p>Name is unique per site.</p>
 */
@Entity
@Table(
    name = "shift_type",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_shift_type_site_name",
        columnNames = {"site_id", "name"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ShiftType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
