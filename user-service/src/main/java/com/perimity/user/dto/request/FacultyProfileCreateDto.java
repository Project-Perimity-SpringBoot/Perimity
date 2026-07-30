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
 * Body of POST /api/user/faculty
 *
 * Same shape as the student profile: the login account is in auth-service, this
 * row is identity information only, and files live in object storage with only
 * the key stored here.
 */
@Schema(description = "Create a faculty member's identity profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacultyProfileCreateDto {

    @NotNull(message = "User account is required")
    @Positive(message = "User id must be a positive number")
    @Schema(example = "42")
    private Long userId;

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(example = "1")
    private Long campusId;

    @Positive(message = "Department id must be a positive number")
    @Schema(nullable = true, example = "3")
    private Long departmentId;

    @Size(max = 32)
    @Pattern(regexp = ValidationPatterns.IDENTIFIER_CODE,
             message = ValidationPatterns.IDENTIFIER_CODE_MESSAGE)
    @Schema(example = "EMP-2041")
    private String employeeId;

    @Size(max = 100)
    @Pattern(regexp = ValidationPatterns.TITLE, message = ValidationPatterns.TITLE_MESSAGE)
    @Schema(example = "Associate Professor")
    private String designation;

    @Size(max = 150)
    @Schema(example = "PhD, Computer Science")
    private String qualification;

    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    private String photoS3Key;
}
