package com.perimity.gatepass.entity;

import com.perimity.gatepass.entity.enums.BatchStatus;
import com.perimity.gatepass.entity.enums.PassType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import com.perimity.gatepass.validation.ValidationPatterns;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One spreadsheet upload. Tracks the two-phase flow: fast validation first,
 * then slow asynchronous generation once the uploader confirms.
 *
 * There is a single bulk engine. passType is the only thing that differs
 * between a student batch (DAILY, no end date) and an event batch (EVENT,
 * dates taken from the event, not from the rows).
 */
@Entity
@Table(
        name = "bulk_upload_batches",
        indexes = {
                @Index(name = "idx_bub_campus_status", columnList = "campus_id, status"),
                @Index(name = "idx_bub_uploaded_by", columnList = "uploaded_by"),
                @Index(name = "idx_bub_event", columnList = "event_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkUploadBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @NotNull
    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    /** DAILY = student onboarding batch. EVENT = event visitor batch. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "pass_type", nullable = false, length = 20)
    private PassType passType;

    /** Required when passType = EVENT. Supplies the date range for every row. */
    @Column(name = "event_id")
    private Long eventId;

    /** Object storage key of the uploaded spreadsheet. */
    @NotBlank
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Column(name = "object_key", nullable = false, length = 300)
    private String objectKey;

    @Pattern(regexp = ValidationPatterns.SPREADSHEET_FILENAME,
             message = ValidationPatterns.SPREADSHEET_FILENAME_MESSAGE)
    @Column(name = "original_filename", length = 260)
    private String originalFilename;

    @Min(0)
    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private int totalRows = 0;

    @Min(0)
    @Column(name = "valid_rows", nullable = false)
    @Builder.Default
    private int validRows = 0;

    @Min(0)
    @Column(name = "invalid_rows", nullable = false)
    @Builder.Default
    private int invalidRows = 0;

    /** Rows already handed to the QR queue. Drives the progress screen. */
    @Min(0)
    @Column(name = "processed_rows", nullable = false)
    @Builder.Default
    private int processedRows = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BatchStatus status = BatchStatus.VALIDATING;

    /** Downloadable report listing each bad row and why it failed. */
    @Column(name = "error_report_key", length = 300)
    private String errorReportKey;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
