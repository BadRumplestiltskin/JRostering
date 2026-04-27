package com.magicsystems.jrostering.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Tracks the lifecycle of a single asynchronous Timefold solve run.
 *
 * <p>Each solve submission creates one {@code SolverJob}. The job transitions through
 * {@link SolverJobStatus} states as the background thread progresses. Persisting the
 * job state means the manager can refresh the page and see the current status without
 * maintaining a live connection to the solver thread.</p>
 *
 * <p>On an {@link SolverJobStatus#INFEASIBLE} result, {@code finalScore} records the
 * negative hard score of the best partial solution found. The partial solution is
 * preserved in {@link ShiftAssignment} rows; unresolvable slots remain {@code null}.</p>
 *
 * <p>Jobs found in {@link SolverJobStatus#RUNNING} or {@link SolverJobStatus#QUEUED}
 * status at application startup are recovered to {@link SolverJobStatus#FAILED} by
 * {@code StartupRecoveryService}.</p>
 */
@Entity
@Table(name = "solver_job")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class SolverJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roster_period_id", nullable = false)
    private RosterPeriod rosterPeriod;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SolverJobStatus status = SolverJobStatus.QUEUED;

    /** Populated when the background thread begins executing the solve. */
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    /** Populated when the solve terminates for any reason. */
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /**
     * Maximum wall-clock time the solver is permitted to run, in seconds.
     * Configurable by the manager at solve submission time.
     */
    @Min(1)
    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    /**
     * The Timefold score string of the final (or best partial) solution,
     * e.g. {@code "0hard/0medium/-42soft"} or {@code "-2hard/0medium/-10soft"}.
     * Populated on {@link SolverJobStatus#COMPLETED} and {@link SolverJobStatus#INFEASIBLE}.
     */
    @Column(name = "final_score", length = 100)
    private String finalScore;

    /**
     * Human-readable explanation of why no feasible solution could be found.
     * Populated only when {@code status = INFEASIBLE}.
     */
    @Column(name = "infeasible_reason", columnDefinition = "TEXT")
    private String infeasibleReason;

    /**
     * Error detail for unexpected solver failures or application restart recovery.
     * Populated only when {@code status = FAILED}.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Per-constraint violation breakdown serialised as a JSON array.
     * Populated on {@link SolverJobStatus#COMPLETED}, {@link SolverJobStatus#INFEASIBLE},
     * and {@link SolverJobStatus#CANCELLED} by {@code SolverTransactionHelper}
     * using data extracted from Timefold's {@code ScoreManager} API.
     *
     * <p>Each element has the shape:
     * {@code {"constraintName":"…","score":"…","violations":N}}.
     * Only constraints with at least one violation are included.</p>
     *
     * <p>Used by {@code ExcelReportGenerator} to produce the Rule Violation Summary report.</p>
     */
    @Column(name = "violation_detail_json", columnDefinition = "jsonb")
    private String violationDetailJson;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
