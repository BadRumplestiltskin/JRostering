package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Specifies that a {@link Shift} requires a minimum number of staff holding
 * a given {@link Qualification}.
 *
 * <p>Evaluated by the solver under the {@code MIN_QUALIFIED_STAFF_PER_SHIFT}
 * and {@code QUALIFICATION_REQUIRED_FOR_SHIFT} hard constraints.</p>
 *
 * <p>Each shift-qualification pair is unique. {@code minimumCount} must be at least 1;
 * enforced at the database level.</p>
 */
@Entity
@Table(
    name = "shift_qualification_requirement",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_shift_qualification_req",
        columnNames = {"shift_id", "qualification_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class ShiftQualificationRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qualification_id", nullable = false)
    private Qualification qualification;

    @Min(1)
    @Column(name = "minimum_count", nullable = false)
    private int minimumCount = 1;
}
