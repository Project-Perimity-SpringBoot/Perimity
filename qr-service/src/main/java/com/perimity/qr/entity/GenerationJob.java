package com.perimity.qr.entity;

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
 * One QR/PDF generation job, consumed from the pass.generate RabbitMQ queue.
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
                @Index(name = "idx_gj_pass", columnList = "pass_id")
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
