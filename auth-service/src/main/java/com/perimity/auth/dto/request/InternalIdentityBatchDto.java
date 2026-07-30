package com.perimity.auth.dto.request;

import com.perimity.auth.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * Body of POST /api/internal/auth/users/batch.
 *
 * Day 10, the SLOW phase - called once after the faculty clicks Confirm. Resolves
 * every row by email in one call: existing identity reused, brand-new email gets
 * a lightweight VISITOR identity, refused rows skipped.
 *
 * Same rule as the single-row version: no role field. This endpoint only ever
 * creates a VISITOR. A batch endpoint that accepted a role would be a
 * spreadsheet that can mint Campus Admins, and the spreadsheet is uploaded by
 * Faculty.
 */
@Schema(description = "Resolve or create many identities by email in one call")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalIdentityBatchDto {

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    private Long campusId;

    @Valid
    @NotEmpty(message = "Provide at least one row")
    private List<Row> rows;

    @Size(max = 60)
    @Schema(description = "Free text for the audit trail", example = "gatepass-bulk-batch-88",
            nullable = true)
    private String source;

    @Schema(description = "One spreadsheet row's identity fields")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Row {

        @Positive(message = "Row number must be positive")
        @Schema(description = "Spreadsheet row number as the uploader sees it", example = "34")
        private Integer rowNumber;

        @NotBlank(message = "Email is required")
        @Size(max = 180)
        @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
        private String email;

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 120)
        @Pattern(regexp = ValidationPatterns.PERSON_NAME,
                 message = ValidationPatterns.PERSON_NAME_MESSAGE)
        private String name;

        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        private String phone;
    }
}
