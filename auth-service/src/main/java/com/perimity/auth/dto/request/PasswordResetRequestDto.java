package com.perimity.auth.dto.request;

import com.perimity.auth.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/auth/password/reset-request
 *
 * This endpoint must ALWAYS return the same success response, whether or not
 * the email belongs to a real account. Anything else turns it into a free
 * account-enumeration tool: an attacker submits ten thousand addresses and
 * keeps the ones that come back differently.
 */
@Schema(description = "Ask for a password reset link. The response never reveals whether the account exists.")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetRequestDto {

    @NotBlank(message = "Email is required")
    @Size(max = 180)
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    private String email;
}
