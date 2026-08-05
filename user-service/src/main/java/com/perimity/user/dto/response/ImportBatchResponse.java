package com.perimity.user.dto.response;

import com.perimity.user.entity.StudentImportBatch;
import com.perimity.user.entity.enums.ImportBatchStatus;
import java.time.LocalDateTime;

/**
 * A bulk import batch, as the faculty screens see it.
 *
 * The counts are the whole point of this shape. A batch that "worked" is not
 * interesting; what a person needs to know is how many rows were refused and
 * how many students came through without a photo - because those two groups
 * are the ones who will turn up at a gate on Monday and be turned away.
 */
public record ImportBatchResponse(
        Long id,
        Long campusId,
        Long uploadedBy,
        String filename,
        ImportBatchStatus status,

        int totalRows,
        int validRows,
        int createdCount,
        int updatedCount,
        int rejectedCount,

        /**
         * Imported, but with no photo - Drive was off, unreachable, or the link
         * was not an image.
         *
         * Surfaced as its own number rather than folded into a success count.
         * These students have an account and verified details and CANNOT hold a
         * pass until they upload a photo, so a screen that quietly counted them
         * as fine would hide a group of people who cannot get in.
         */
        int missingPhotoCount,

        String failureReason,
        LocalDateTime confirmedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,

        /** UI hint. The server refuses a second confirm regardless. */
        boolean confirmable
) {

    public static ImportBatchResponse from(StudentImportBatch e) {
        return new ImportBatchResponse(
                e.getId(),
                e.getCampusId(),
                e.getUploadedBy(),
                e.getFilename(),
                e.getStatus(),
                e.getTotalRows(),
                e.getValidRows(),
                e.getCreatedCount(),
                e.getUpdatedCount(),
                e.getRejectedCount(),
                e.getMissingPhotoCount(),
                e.getFailureReason(),
                e.getConfirmedAt(),
                e.getFinishedAt(),
                e.getCreatedAt(),
                e.getStatus().isConfirmable());
    }
}
