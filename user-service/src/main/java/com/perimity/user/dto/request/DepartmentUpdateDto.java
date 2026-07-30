package com.perimity.user.dto.request;

import com.perimity.user.validation.ValidationPatterns;
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
 * Body of PUT /api/user/departments/{id}
 *
 * code and campusId are absent: profiles already point at this department, and
 * renaming its code would break every report that groups by it.
 *
 * Deactivating rather than deleting is deliberate - a department with students
 * attached must not vanish, it must stop appearing in new dropdowns. The
 * service layer should refuse to deactivate one that still has active profiles,
 * using StudentProfileRepository.findByDepartmentId.
 */
@Schema(description = "Rename a department, or retire it from new selections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentUpdateDto {

    @NotBlank(message = "Department name is required")
    @Size(max = 150)
    @Pattern(regexp = ValidationPatterns.TITLE, message = ValidationPatterns.TITLE_MESSAGE)
    private String name;

    @NotNull(message = "Active state is required")
    @Schema(description = "false hides it from new profile forms; existing profiles keep it",
            example = "true")
    private Boolean active;
}
