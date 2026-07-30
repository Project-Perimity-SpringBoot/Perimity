package com.perimity.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
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
 * Body of POST /api/auth/users - a Super Admin or Campus Admin creates an account.
 *
 * temporaryPassword is the PLAIN password. It is hashed with bcrypt in the
 * service layer and the plain value is never stored, never logged and never
 * echoed back. That is why PASSWORD_POLICY is applied here and not on the
 * entity: by the time a value reaches the entity it is a hash, and a hash can
 * never satisfy the policy regex.
 *
 * The two @AssertTrue rules below mirror the ones already on the User entity.
 * Duplicating them here is intentional - the entity check is the last line of
 * defence, but it fires as a 500-ish ConstraintViolation deep in the save. The
 * DTO check turns the same mistake into a clean 400 with a readable message.
 */
@Schema(description = "Admin creates a Faculty, Guard, Student or Campus Admin account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCreateDto {

    @NotBlank(message = "Email is required")
    @Size(max = 180)
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Schema(example = "r.kulkarni@north-campus.example.com")
    private String email;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 120)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
    @Schema(example = "Rohit Kulkarni")
    private String name;

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    private String phone;

    @NotNull(message = "Role is required")
    @Schema(description = "SUPER_ADMIN, CAMPUS_ADMIN, FACULTY, STUDENT, GUARD or VISITOR",
            example = "FACULTY")
    private Role role;

    @Positive(message = "Campus id must be a positive number")
    @Schema(description = "Required for every role except SUPER_ADMIN, who is platform-wide",
            nullable = true, example = "1")
    private Long campusId;

    @Size(max = 72, message = "Password must not exceed 72 characters")
    @Pattern(regexp = ValidationPatterns.PASSWORD_POLICY,
             message = ValidationPatterns.PASSWORD_POLICY_MESSAGE)
    @Schema(description = "Plain password, hashed server-side. Omit for a VISITOR.",
            nullable = true, example = "TempPass123")
    private String temporaryPassword;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "A visitor must not be given a password; every other role requires one")
    public boolean isPasswordConsistentWithRole() {
        if (role == null) {
            return true;
        }
        boolean supplied = temporaryPassword != null && !temporaryPassword.isBlank();
        return role.canLoginWithPassword() == supplied;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Only a Super Admin may have no campus; every other role needs one")
    public boolean isCampusConsistentWithRole() {
        if (role == null) {
            return true;
        }
        return role.requiresCampus() == (campusId != null);
    }
}
