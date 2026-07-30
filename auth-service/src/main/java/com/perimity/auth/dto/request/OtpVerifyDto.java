package com.perimity.auth.dto.request;

import com.perimity.auth.entity.enums.OtpPurpose;
import com.perimity.auth.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/auth/otp/verify
 *
 * The client sends the PLAIN six-digit code. The service hashes it with SHA-256
 * and compares against otp_hash. The plain code is never stored and never
 * logged - not in the audit details field either.
 *
 * purpose must match the purpose the code was issued for.
 */
@Schema(description = "Submit a one-time code for verification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpVerifyDto {

    @NotBlank(message = "Email is required")
    @Size(max = 180)
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    private String email;

    @NotNull(message = "Purpose is required")
    @Schema(description = "Must match the purpose the code was issued for", example = "LOGIN")
    private OtpPurpose purpose;

    @NotBlank(message = "The code is required")
    @Pattern(regexp = "^\\d{6}$", message = "The code must be exactly 6 digits")
    @Schema(description = "Plain code as typed by the user. Hashed server-side, never stored.",
            example = "418902")
    private String code;
}
