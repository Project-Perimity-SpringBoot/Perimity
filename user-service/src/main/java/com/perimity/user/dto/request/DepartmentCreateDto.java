package com.perimity.user.dto.request;

import com.perimity.user.validation.ValidationPatterns;
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
 * Body of POST /api/user/departments
 *
 * Departments are per-campus seeded data, entered by that campus's admin. There
 * is no platform-wide department list and no enum of department names anywhere
 * in this codebase. The same code may legitimately repeat across campuses.
 *
 * "Is this code already used on this campus" is answered by
 * DepartmentRepository.existsByCampusIdAndCodeIgnoreCase, not by a regex.
 */
@Schema(description = "Seed a department for one campus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentCreateDto {

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(example = "1")
    private Long campusId;

    @NotBlank(message = "Department code is required")
    @Size(max = 32)
    @Pattern(regexp = ValidationPatterns.DEPARTMENT_CODE,
             message = ValidationPatterns.DEPARTMENT_CODE_MESSAGE)
    @Schema(description = "Whatever short token this campus uses. Unique within the campus only.",
            example = "CS")
    private String code;

    @NotBlank(message = "Department name is required")
    @Size(max = 150)
    @Pattern(regexp = ValidationPatterns.TITLE, message = ValidationPatterns.TITLE_MESSAGE)
    @Schema(example = "Computer Science")
    private String name;
}
