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
 * A physical or logical location within an {@link Organisation}.
 * Each site has its own {@link RuleConfiguration} set, {@link RosterPeriod} history,
 * and {@link ShiftType} reference data.
 *
 * <p>The {@code timezone} field stores an IANA timezone identifier
 * (e.g. {@code Australia/Adelaide}) and is used when interpreting shift datetimes.</p>
 */
@Entity
@Table(name = "site")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String name;

    /**
     * IANA timezone identifier for this site (e.g. {@code Australia/Adelaide}).
     * All {@link Shift} start/end datetimes for this site are interpreted in this zone.
     */
    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String timezone;

    @Size(max = 500)
    @Column(length = 500)
    private String address;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
