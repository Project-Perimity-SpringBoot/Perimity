package com.perimity.auth.dto.request;

import com.perimity.auth.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/internal/auth/users.
 *
 * Called by another service - today, gatepass-service's bulk engine resolving
 * a spreadsheet row by email. Same shape as VisitorRegistrationDto and the
 * same reason: this endpoint only ever creates a VISITOR, so role is not a
 * field a caller can set. Widening it to accept a role would turn a bulk
 * upload into a path that can mint a Campus Admin.
 *
 * source is free text for the audit trail only - "gatepass-bulk-batch-88" -
 * so a look at audit_logs later says which service and which flow created
 * this identity, not just that "something internal" did.
 */
@Schema(description = "Another service resolves or creates a lightweight VISITOR identity by email")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalIdentityCreateDto {

    @NotBlank(message = "Email is required")
    @Size(max = 180)
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    private String email;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 120)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
    private String name;

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    private String phone;

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(description = "The campus this identity is being created against - the hosting "
            + "campus for an event visitor, for example")
    private Long campusId;

    @Size(max = 60)
    @Schema(description = "Free text for the audit trail - which service and flow created this",
            example = "gatepass-bulk-batch-88", nullable = true)
    private String source;
}
