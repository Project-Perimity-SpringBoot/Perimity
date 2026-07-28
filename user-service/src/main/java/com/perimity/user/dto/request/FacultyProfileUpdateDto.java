package com.perimity.user.dto.request;

import com.perimity.user.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PUT /api/user/faculty/{id}
 *
 * userId and campusId absent for the same reason as on the student profile.
 * photoS3Key and employeeId are SENSITIVE - changing either pauses the pass.
 */
@Schema(description = "Edit a faculty profile. Changing a sensitive field pauses the holder's pass.")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacultyProfileUpdateDto {

    @Positive(message = "Department id must be a positive number")
    private Long departmentId;

    @Size(max = 32)
    @Pattern(regexp = ValidationPatterns.IDENTIFIER_CODE,
             message = ValidationPatterns.IDENTIFIER_CODE_MESSAGE)
    @Schema(description = "SENSITIVE - changing this pauses the pass")
    private String employeeId;

    @Size(max = 100)
    @Pattern(regexp = ValidationPatterns.TITLE, message = ValidationPatterns.TITLE_MESSAGE)
    private String designation;

    @Size(max = 150)
    private String qualification;

    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Schema(description = "SENSITIVE - changing this pauses the pass")
    private String photoS3Key;
}
