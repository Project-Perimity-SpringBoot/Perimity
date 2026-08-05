package com.perimity.user.entity;

import com.perimity.user.entity.enums.ImportBatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
 * One upload of a Google Form responses sheet.
 *
 * See docs/BULK_STUDENT_ONBOARDING.md for the whole flow.
 *
 * ==========================================================================
 * WHY THE BATCH IS PERSISTED AND NOT HELD IN MEMORY
 * ==========================================================================
 * Validation and confirmation are two separate requests, possibly minutes
 * apart while faculty read the preview. A batch parked in a map would vanish on
 * restart and could not be shared across instances.
 *
 * It also has to outlive the import. When somebody asks in March where a
 * student came from, "batch 14, uploaded by faculty 6 on 5 August from
 * Student Details 2026.xlsx" is the answer, and it is also the audit trail for
 * every profile that batch marked VERIFIED.
 *
 * ==========================================================================
 * uploadedBy IS THE VERIFIER
 * ==========================================================================
 * Rows import as VERIFIED and each profile's verifiedBy is set to this account.
 * That is the person who chose the file, read the preview and confirmed - a
 * true statement about who took responsibility.
 *
 * It must never be null. A batch that cannot name its uploader cannot honestly
 * verify anybody.
 *
 * THE SHEET ITSELF IS NOT STORED. It holds every student's date of birth,
 * address and phone number, and keeping a spare copy of that in a second place
 * doubles the surface for no benefit - the rows are already in the profiles.
 * Only the filename is kept, so a person can recognise which upload this was.
 */
@Entity
@Table(
        name = "student_import_batches",
        indexes = {
                @Index(name = "idx_import_batch_campus", columnList = "campus_id"),
                @Index(name = "idx_import_batch_uploader", columnList = "uploaded_by")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Campus-scoped like everything else. Comes from the uploader's token. */
    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    /**
     * The faculty or admin who uploaded. Becomes verifiedBy on every profile
     * this batch creates, so it is the answer to "who says these details are
     * right".
     */
    @NotNull
    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    /** For recognition only. The file is not kept. */
    @Size(max = 255)
    @Column(name = "filename", length = 255)
    private String filename;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ImportBatchStatus status = ImportBatchStatus.VALIDATING;

    /* ------------------------------------------------------------------
     * Counts. Denormalised on purpose: the progress screen polls every two
     * seconds, and counting rows on each poll would mean a scan per poll per
     * viewer. Kept in step by the service that writes the rows.
     * ------------------------------------------------------------------ */

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private int totalRows = 0;

    /** Rows that parsed and passed validation. Eligible to be written. */
    @Column(name = "valid_rows", nullable = false)
    @Builder.Default
    private int validRows = 0;

    @Column(name = "created_count", nullable = false)
    @Builder.Default
    private int createdCount = 0;

    @Column(name = "updated_count", nullable = false)
    @Builder.Default
    private int updatedCount = 0;

    @Column(name = "rejected_count", nullable = false)
    @Builder.Default
    private int rejectedCount = 0;

    /**
     * Rows imported with no photo, because Drive was off or the fetch failed.
     *
     * Tracked separately and prominently: those students have a profile but
     * CANNOT hold a pass, since a guard would have no face to check. A count
     * buried as "success" would hide a group of people who cannot get through
     * the gate on Monday.
     */
    @Column(name = "missing_photo_count", nullable = false)
    @Builder.Default
    private int missingPhotoCount = 0;

    /** Why the batch as a whole failed. Null unless status is FAILED. */
    @Size(max = 500)
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
