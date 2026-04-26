package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores the configuration for a single scheduling rule applied to a {@link Site}.
 * One row per {@link RuleType} per site.
 *
 * <p>The {@code enabled} flag is the sole activation switch — no other layer
 * suppresses a rule. When {@code false}, the solver ignores this rule entirely.</p>
 *
 * <p>{@code constraintLevel} maps the rule to the appropriate component of the
 * {@code HardMediumSoftScore}. Most rules have a fixed default level; only
 * {@code STAFF_AVAILABILITY_BLOCK} is designed to be reclassified to
 * {@link ConstraintLevel#MEDIUM} for sites with advisory availability.</p>
 *
 * <p>{@code weight} is only meaningful for {@link ConstraintLevel#SOFT} rules.
 * Higher values indicate greater importance relative to other soft rules.</p>
 *
 * <p>{@code parameterJson} holds rule-specific parameters as a JSONB string.
 * Rules with no parameters store {@code "{}"}. The service layer is responsible
 * for deserialising this value into the appropriate parameter object per rule type.</p>
 */
@Entity
@Table(
    name = "rule_configuration",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_rule_configuration_site_type",
        columnNames = {"site_id", "rule_type"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class RuleConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 100)
    private RuleType ruleType;

    @Column(nullable = false)
    private boolean enabled = true;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "constraint_level", nullable = false, length = 10)
    private ConstraintLevel constraintLevel;

    /**
     * Relative importance for {@link ConstraintLevel#SOFT} rules.
     * {@code null} for HARD and MEDIUM rules.
     */
    @Column
    private Integer weight;

    /**
     * Rule-specific parameters as a JSONB document. Rules with no parameters store {@code "{}"}.
     * Deserialisaton to a typed parameter object is the responsibility of the service layer.
     */
    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameter_json", columnDefinition = "jsonb", nullable = false)
    private String parameterJson = "{}";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // =========================================================================
    // Parameter helpers — used by the constraint provider during solving
    // =========================================================================

    /**
     * Compiled pattern for parsing integer values from flat JSON parameter documents.
     * Matches {@code "key": value} where value is a non-negative integer.
     */
    private static final Pattern INT_PARAM_RE =
            Pattern.compile("\"(\\w+)\"\\s*:\\s*(\\d+)");

    /**
     * Lazy-initialised cache of parsed integer parameters.
     * Populated on first access; safe for single-threaded solver evaluation.
     */
    @jakarta.persistence.Transient
    private transient java.util.Map<String, Integer> cachedIntParams;

    /**
     * Reads an integer value from {@link #parameterJson} by key.
     * The JSON is parsed lazily and the result cached for the lifetime of this object.
     *
     * <p>Only flat, single-level JSON with integer values is supported — matching
     * the parameter schemas defined in the architecture document (Section 6).</p>
     *
     * @param key          the JSON property name
     * @param defaultValue returned when the key is absent or the JSON is empty
     * @return the integer value associated with {@code key}, or {@code defaultValue}
     */
    public int getIntParam(String key, int defaultValue) {
        if (cachedIntParams == null) {
            cachedIntParams = new java.util.HashMap<>();
            if (parameterJson != null && !parameterJson.isBlank() && !parameterJson.equals("{}")) {
                Matcher m = INT_PARAM_RE.matcher(parameterJson);
                while (m.find()) {
                    cachedIntParams.put(m.group(1), Integer.parseInt(m.group(2)));
                }
            }
        }
        return cachedIntParams.getOrDefault(key, defaultValue);
    }

    /**
     * Returns the configured soft weight, or {@code fallback} if weight is null.
     * Convenience method for the constraint provider.
     */
    public int weightOrDefault(int fallback) {
        return weight != null ? weight : fallback;
    }
}
