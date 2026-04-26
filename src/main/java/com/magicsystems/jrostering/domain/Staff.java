package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A person who can be rostered onto shifts within an {@link Organisation}.
 *
 * <p>A staff member belongs to one organisation but may be assigned to multiple
 * sites via {@link StaffSiteAssignment}. Qualifications, availability windows,
 * leave, and preferences are tracked through their respective association entities.</p>
 *
 * <p>Email is unique per organisation.</p>
 */
@Entity
@Table(
    name = "staff",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_staff_org_email",
        columnNames = {"organisation_id", "email"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(nullable = false)
    private String email;

    @Size(max = 50)
    @Column(length = 50)
    private String phone;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 50)
    private EmploymentType employmentType;

    /**
     * Contracted hours per week. {@code null} for casual staff with no contracted hours.
     */
    @Column(name = "contracted_hours_per_week", precision = 5, scale = 2)
    private BigDecimal contractedHoursPerWeek;

    /**
     * Hourly rate. Stored for potential future payroll integration; not used by the solver.
     */
    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
