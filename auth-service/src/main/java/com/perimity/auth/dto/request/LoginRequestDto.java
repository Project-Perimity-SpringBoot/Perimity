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
 * Body of POST /api/auth/login - password login.
 *
 * NOTE the password is checked only for presence and length here, NOT against
 * PASSWORD_POLICY. Applying the policy on login would tell an attacker the
 * shape of every valid password before they ever guess one, and would lock out
 * any account seeded before the policy tightened. The policy belongs on the
 * endpoints that SET a password, and nowhere else.
 *
 * Valid for every role except VISITOR, who has no password at all. Role.canLoginWithPassword()
 * is the single source of that truth.
 */
@Schema(description = "Password login. Visitors cannot use this endpoint.")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequestDto {

    @NotBlank(message = "Email is required")
    @Size(max = 180)
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Schema(example = "admin@north-campus.example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 1, max = 72, message = "Password must not exceed 72 characters")
    @Schema(description = "72 is bcrypt's hard input limit, not a policy choice",
            example = "CorrectHorse1")
    private String password;
}
