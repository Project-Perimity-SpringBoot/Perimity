package com.perimity.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.perimity.auth.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/auth/password/reset-confirm
 *
 * token is the PLAIN value from the emailed link. The service hashes it with
 * SHA-256 and looks up password_resets by token_hash - which is why only the
 * hash is ever stored. A database leak yields no usable reset links.
 *
 * The token is a 64-character hex string, matching how the row stores it, so
 * SHA256_HEX validates the shape before a database round trip is spent on it.
 */
@Schema(description = "Complete a password reset using the emailed token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetConfirmDto {

    @NotBlank(message = "The reset token is required")
    @Pattern(regexp = ValidationPatterns.SHA256_HEX, message = "Invalid or malformed reset token")
    @Schema(description = "The token from the emailed link, 64 hex characters")
    private String token;

    @NotBlank(message = "A new password is required")
    @Size(max = 72, message = "Password must not exceed 72 characters")
    @Pattern(regexp = ValidationPatterns.PASSWORD_POLICY,
             message = ValidationPatterns.PASSWORD_POLICY_MESSAGE)
    private String newPassword;

    @NotBlank(message = "Please confirm the new password")
    @Size(max = 72)
    private String confirmPassword;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "The new password and its confirmation do not match")
    public boolean isConfirmationMatching() {
        if (newPassword == null || confirmPassword == null) {
            return true;
        }
        return newPassword.equals(confirmPassword);
    }
}
