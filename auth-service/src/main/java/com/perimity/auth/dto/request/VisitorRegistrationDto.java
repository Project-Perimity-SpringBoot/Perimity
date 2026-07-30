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
 * Body of POST /api/auth/visitors/register - visitor self-service (SRS v1.1).
 *
 * No password field, and there must never be one. A VISITOR authenticates by
 * email plus OTP for life; Role.canLoginWithPassword() returns false for them,
 * and the User entity's own @AssertTrue rejects a visitor row that carries a
 * password hash.
 *
 * role is absent too - this endpoint only ever creates a VISITOR. Letting a
 * client name its own role is how a self-service form becomes an admin factory.
 *
 * The blocklist check happens in the service layer. Per FR-BLK-4 the refusal
 * must be deliberately vague: never tell the person they are blocklisted.
 */
@Schema(description = "A visitor registers themselves. OTP only, never a password.")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitorRegistrationDto {

    @NotBlank(message = "Email is required")
    @Size(max = 180)
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Schema(example = "anita.deshmukh@example.com")
    private String email;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 120)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
    @Schema(example = "Anita Deshmukh")
    private String name;

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    @Schema(example = "+919876543210")
    private String phone;

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(description = "Which campus the visitor is registering for", example = "1")
    private Long campusId;
}
