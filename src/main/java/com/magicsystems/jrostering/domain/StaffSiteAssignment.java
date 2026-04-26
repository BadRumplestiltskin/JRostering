package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Associates a {@link Staff} member with a {@link Site}, allowing cross-site rostering.
 *
 * <p>A staff member may be assigned to multiple sites. Exactly one assignment per
 * staff member should have {@code primarySite = true}.</p>
 *
 * <p>Each staff-site pair is unique.</p>
 */
@Entity
@Table(
    name = "staff_site_assignment",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_staff_site",
        columnNames = {"staff_id", "site_id"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class StaffSiteAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "primary_site", nullable = false)
    private boolean primarySite = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
