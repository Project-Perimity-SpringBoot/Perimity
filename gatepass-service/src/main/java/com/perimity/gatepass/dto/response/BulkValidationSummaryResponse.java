package com.perimity.gatepass.dto.response;

import com.perimity.gatepass.entity.BulkUploadBatch;
import java.util.List;

/**
 * What the uploader sees roughly two seconds after uploading: "580 valid,
 * 20 errors". Nothing has been created at this point - the batch sits at
 * VALIDATED until a BulkConfirmDto arrives.
 *
 * The error list is capped by the service layer; errorReportKey points at the
 * full downloadable report for a sheet with hundreds of bad rows.
 */
public record BulkValidationSummaryResponse(
        Long batchId,
        int totalRows,
        int validRows,
        int invalidRows,
        List<RowErrorResponse> errors,
        String errorReportKey,
        boolean awaitingConfirmation
) {

    public static BulkValidationSummaryResponse from(BulkUploadBatch e, List<RowErrorResponse> errors) {
        return new BulkValidationSummaryResponse(
                e.getId(),
                e.getTotalRows(),
                e.getValidRows(),
                e.getInvalidRows(),
                errors == null ? List.of() : List.copyOf(errors),
                e.getErrorReportKey(),
                e.getValidRows() > 0
        );
    }
}
