package com.perimity.campus.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller gets after a report is stored, and what the uploader gets
 * when they ask to download it.
 *
 * The KEY goes in gatepass-service's bulk_upload_batches.error_report_key. The
 * URL never does - it is signed, short-lived, and meaningless tomorrow. That
 * distinction is the rule from the Database Design document: keys in the
 * database, URLs generated on demand.
 */
@Schema(description = "Where a stored error report lives")
public record BulkErrorReportResponse(

        @Schema(description = "Object key. Store THIS on the batch record.",
                example = "bulk/north-campus/88/errors.csv")
        String key,

        @Schema(description = "Failed rows in the report", example = "20")
        int rowCount,

        @Schema(description = "Short-lived download link, or null when the caller only "
                + "stored the report and did not ask for one",
                example = "/api/campus/storage/local/bulk/north-campus/88/errors.csv",
                nullable = true)
        String url
) {

    public static BulkErrorReportResponse stored(String key, int rowCount) {
        return new BulkErrorReportResponse(key, rowCount, null);
    }

    public static BulkErrorReportResponse downloadable(String key, String url) {
        return new BulkErrorReportResponse(key, 0, url);
    }
}
