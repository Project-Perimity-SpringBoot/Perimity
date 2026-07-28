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
 * Body of POST /api/auth/password/change - a signed-in user changes their own.
 *
 * There is no userId field. The account comes from the authenticated principal,
 * never from the request body. If a client could name the account, anyone could
 * change anyone's password.
 *
 * currentPassword carries no policy check - it is an existing value being
 * verified, not a new one being set.
 */
@Schema(description = "Change your own password")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordChangeDto {

    @NotBlank(message = "Your current password is required")
    @Size(max = 72)
    private String currentPassword;

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

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "The new password must be different from the current one")
    public boolean isNewPasswordDifferent() {
        if (newPassword == null || currentPassword == null) {
            return true;
        }
        return !newPassword.equals(currentPassword);
    }
}
