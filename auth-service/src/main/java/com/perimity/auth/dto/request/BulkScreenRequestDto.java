package com.perimity.auth.dto.request;

import com.perimity.auth.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/internal/auth/blocklist/screen.
 *
 * Day 10. Screen a whole spreadsheet against one campus's blocklist in a single
 * call, during the FAST validation phase - before any identity is created and
 * before the faculty has clicked Confirm.
 *
 * This is a pure read. It creates nothing, so the bulk engine can call it while
 * the uploader is still deciding, and call it again after they fix rows, with no
 * side effects either time.
 *
 * rowNumber is the SPREADSHEET row number, passed through untouched and echoed
 * back. Event_Bulk_Design.md wants the error report to say "row 34: invalid
 * email" - row 34 as the faculty sees it in Excel, not index 33 of an array
 * after 12 header and blank rows were skipped. Only the caller knows that
 * offset, so only the caller can supply it.
 */
@Schema(description = "Screen many rows against one campus's blocklist in one call")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkScreenRequestDto {

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(description = "The blocklist to screen against - the hosting campus")
    private Long campusId;

    /**
     * @Valid is load-bearing. Without it none of the constraints on Candidate
     * run and the endpoint accepts a list of empty objects in silence.
     */
    @Valid
    @NotEmpty(message = "Provide at least one row to screen")
    private List<Candidate> rows;

    @Size(max = 60)
    @Schema(description = "Free text for the audit trail - which batch this screening was for",
            example = "gatepass-bulk-batch-88", nullable = true)
    private String source;

    @Schema(description = "One spreadsheet row, reduced to just what screening needs")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Candidate {

        @Positive(message = "Row number must be positive")
        @Schema(description = "The row number in the uploaded spreadsheet, as the "
                + "uploader sees it. Echoed back unchanged.", example = "34")
        private Integer rowNumber;

        @Size(max = 180)
        @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
        private String email;

        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        private String phone;

        /**
         * Mirrors the same rule on the BlocklistEntry entity. A row with
         * neither field cannot be screened against anything, and silently
         * passing it would mean an unscreenable row reads as "allowed".
         */
        @AssertTrue(message = "Each row needs an email address or a phone number to screen")
        public boolean isEmailOrPhonePresent() {
            return (email != null && !email.isBlank()) || (phone != null && !phone.isBlank());
        }
    }
}
