package com.perimity.campus.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/campus/internal/campuses/{campusId}/bulk/{batchId}/error-report.
 *
 * gatepass-service sends the FAILED ROWS AS DATA and this service renders the
 * CSV. The other way round - gatepass builds a CSV and posts the bytes - was
 * the obvious design and is worse:
 *
 *   - CSV escaping, the Excel BOM and formula-injection defence would have to
 *     be repeated in every service that ever produces a report, and only have
 *     to be forgotten once.
 *   - Bytes cannot be re-rendered. Data can: if the format needs a column added
 *     for the demo, it changes here and nobody else rebuilds anything.
 *   - A service that accepts arbitrary uploaded bytes and serves them back to a
 *     browser is a file-hosting endpoint. This one can only ever emit a CSV it
 *     built itself from validated fields.
 */
@Schema(description = "The failed rows of one bulk upload, rendered here as a downloadable CSV")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkErrorReportDto {

    /**
     * @Valid is load-bearing. Without it none of the constraints on RowError
     * run and a list of empty objects is accepted in silence.
     */
    @Valid
    @NotEmpty(message = "An error report needs at least one failed row")
    @Size(max = 10000, message = "Too many error rows for a single report")
    private List<RowError> rows;

    @Size(max = 200)
    @Schema(description = "Original spreadsheet filename, for the report header",
            example = "summit-attendees.xlsx", nullable = true)
    private String sourceFilename;

    @Schema(description = "One row that failed validation")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RowError {

        @NotNull(message = "Row number is required")
        @Positive(message = "Row number must be positive")
        @Schema(description = "The row number as the uploader sees it in Excel - not an "
                + "array index. Only the caller knows how many header rows it skipped.",
                example = "34")
        private Integer rowNumber;

        @Size(max = 180)
        @Schema(description = "The email on that row, if it had one worth showing back",
                example = "not-an-email", nullable = true)
        private String email;

        /**
         * Free text, and deliberately not an enum. The uploader has to act on
         * this, so it must be able to say "duplicate of row 12" - which no
         * fixed vocabulary can express.
         *
         * FR-BLK-4 constrains what may go in here for a blocked row: "cannot be
         * registered", never "blocklisted". Enforced at the source, in
         * gatepass-service - this service cannot tell one message from another
         * and must not try.
         */
        @NotBlank(message = "Every error row needs a message")
        @Size(max = 300)
        @Schema(description = "What was wrong with the row, in words the uploader can act on",
                example = "Invalid email address")
        private String message;
    }
}
