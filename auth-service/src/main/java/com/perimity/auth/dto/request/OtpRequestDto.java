package com.perimity.auth.dto.request;

import com.perimity.auth.entity.enums.OtpPurpose;
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
 * Body of POST /api/auth/otp/request
 *
 * purpose is mandatory because an OTP issued for one flow must never be
 * replayable in another - a login code must not unlock a password reset.
 * OtpVerification stores it for exactly that reason.
 *
 * The response must be identical whether or not the email exists. Otherwise
 * this endpoint becomes a free account-enumeration oracle.
 */
@Schema(description = "Ask for a one-time code to be emailed")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpRequestDto {

    @NotBlank(message = "Email is required")
    @Size(max = 180)
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Schema(example = "anita.deshmukh@example.com")
    private String email;

    @NotNull(message = "Purpose is required")
    @Schema(description = "LOGIN, REGISTRATION, VISITOR_VERIFICATION, PASS_RETRIEVAL or PASSWORD_RESET",
            example = "VISITOR_VERIFICATION")
    private OtpPurpose purpose;

    @Positive(message = "Campus id must be a positive number")
    @Schema(description = "Scopes the code to one campus where the flow needs it", nullable = true)
    private Long campusId;
}
