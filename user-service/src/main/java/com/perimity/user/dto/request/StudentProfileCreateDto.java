package com.perimity.user.dto.request;

import com.perimity.user.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * Body of POST /api/user/students
 *
 * NO semester field. The SRS is explicit: semester is not needed for access
 * control and must never appear in any form. Do not add one here later.
 *
 * departmentId points at a Department row that the Campus Admin seeded. There
 * is no hard-coded department list anywhere in this service, and there must
 * never be one - two campuses can have completely different departments.
 */
@Schema(description = "Create a student's identity profile. The login account itself lives in auth-service.")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentProfileCreateDto {

    @NotNull(message = "User account is required")
    @Positive(message = "User id must be a positive number")
    @Schema(description = "The auth-service account this profile belongs to. One profile per account.",
            example = "108")
    private Long userId;

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(example = "1")
    private Long campusId;

    @Positive(message = "Department id must be a positive number")
    @Schema(description = "Must be a department seeded for this campus", nullable = true, example = "3")
    private Long departmentId;

    @Size(max = 32)
    @Pattern(regexp = ValidationPatterns.IDENTIFIER_CODE,
             message = ValidationPatterns.IDENTIFIER_CODE_MESSAGE)
    @Schema(description = "Campus-agnostic: institutions format these very differently",
            example = "2026/CS/0141")
    private String rollNo;

    @Pattern(regexp = "^$|^\\d{12}$", message = "Government ID must be 12 digits")
    @Schema(description = "Sensitive. Never echoed back in full - responses return it masked.",
            example = "123456789012")
    private String govId;

    @Size(max = 250)
    private String address;

    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Schema(description = "Storage key only, never the image bytes",
            example = "profiles/user-108/photo.jpg")
    private String photoS3Key;
}
