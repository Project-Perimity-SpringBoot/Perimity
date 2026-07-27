package com.perimity.gatepass.dto.response;

import com.perimity.gatepass.entity.BulkUploadBatch;
import com.perimity.gatepass.entity.enums.BatchStatus;
import com.perimity.gatepass.entity.enums.PassType;
import java.time.LocalDateTime;

/**
 * Read model for a bulk batch. This is what the Bulk Progress screen polls.
 *
 * percentComplete is derived here so the React screen never has to divide by
 * zero on a batch whose validation has not finished yet.
 */
public record BulkUploadBatchResponse(
        Long id,
        Long campusId,
        Long uploadedBy,
        PassType passType,
        Long eventId,
        String objectKey,
        String originalFilename,
        BatchStatus status,
        int totalRows,
        int validRows,
        int invalidRows,
        int processedRows,
        int percentComplete,
        String errorReportKey,
        String failureMessage,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static BulkUploadBatchResponse from(BulkUploadBatch e) {
        int percent = e.getValidRows() <= 0
                ? 0
                : Math.min(100, (int) Math.round(e.getProcessedRows() * 100.0 / e.getValidRows()));

        return new BulkUploadBatchResponse(
                e.getId(),
                e.getCampusId(),
                e.getUploadedBy(),
                e.getPassType(),
                e.getEventId(),
                e.getObjectKey(),
                e.getOriginalFilename(),
                e.getStatus(),
                e.getTotalRows(),
                e.getValidRows(),
                e.getInvalidRows(),
                e.getProcessedRows(),
                percent,
                e.getErrorReportKey(),
                e.getFailureMessage(),
                e.getCompletedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
