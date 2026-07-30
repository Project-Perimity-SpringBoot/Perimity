package com.perimity.qr.entity;

import com.perimity.qr.entity.enums.EmailStatus;
import com.perimity.qr.entity.enums.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One QR/PDF generation job, consumed from the qr.generate.request RabbitMQ queue.
 *
 * batchId groups the jobs from one bulk upload so the Bulk Progress screen can
 * report "412 of 580 done" without gatepass-service polling every pass.
 */
@Entity
@Table(
        name = "generation_jobs",
        indexes = {
                @Index(name = "idx_gj_status", columnList = "status"),
                @Index(name = "idx_gj_batch", columnList = "batch_id"),
                @Index(name = "idx_gj_batch_status", columnList = "batch_id, status"),
                @Index(name = "idx_gj_pass", columnList = "pass_id"),
                @Index(name = "idx_gj_job_ref", columnList = "job_ref"),
                @Index(name = "idx_gj_email", columnList = "email_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "pass_id", nullable = false)
    private Long passId;

    /** Null for a single approval. Set for every row of a bulk upload. */
    @Column(name = "batch_id")
    private Long batchId;

    /**
     * Tushar's QrGenerationJob.jobId - the UUID he generates at publish time.
     * This is the idempotency key.
     *
     * Named jobRef rather than jobId on purpose: this entity's own primary key
     * is already surfaced as "jobId" by JobStatusResponse, and two different
     * values called jobId in one service is how the wrong one ends up in a log
     * line at 1am. Column job_ref, field jobRef, and the Javadoc says whose it
     * is.
     *
     * Nullable, not unique. Nullable because the column is added to rows that
     * already exist under ddl-auto=update. NOT unique because a UNIQUE column
     * turns a redelivery into a DataIntegrityViolationException - so the
     * duplicate would surface as a crash rather than as the normal, expected
     * event it is. The index gives the lookup speed; the decision belongs in
     * GenerationJobService.claim.
     */
    @Size(max = 64)
    @Column(name = "job_ref", length = 64)
    private String jobRef;

    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.QUEUED;

    @Min(0)
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Size(max = 1000)
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * DAY 9. Whether the pass email has gone out for this job.
     *
     * Kept on the job rather than on QrRecord because it is an outcome of this
     * unit of work, not a property of the QR itself - a re-issue produces a new
     * job and a new email, and the previous job's send should stay recorded
     * against the attempt that made it.
     *
     * Defaults to PENDING so a row created before the email is attempted reads
     * correctly rather than as a null nobody can interpret.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 20)
    @Builder.Default
    private EmailStatus emailStatus = EmailStatus.PENDING;

    @Size(max = 500)
    @Column(name = "email_error", length = 500)
    private String emailError;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
