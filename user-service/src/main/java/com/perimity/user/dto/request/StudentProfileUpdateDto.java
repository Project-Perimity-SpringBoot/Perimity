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
 * Body of PUT /api/user/students/{id}
 *
 * userId and campusId are absent on purpose. A profile cannot be reassigned to
 * a different login account or moved to another campus - both would silently
 * break the passes already issued against it.
 *
 * IMPORTANT - the pause rule (SRS v1.1):
 * govId, photoS3Key and rollNo are SENSITIVE fields. When any of them changes,
 * the service layer must tell gatepass-service to move the holder's pass to
 * PAUSED so it can be re-approved. A changed photo with a still-active pass is
 * exactly the hole this rule closes. The DTO cannot enforce that - it is a
 * cross-service action - but every field below that carries the rule is marked.
 */
@Schema(description = "Edit a student profile. Changing a sensitive field pauses the holder's pass.")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentProfileUpdateDto {

    @Positive(message = "Department id must be a positive number")
    private Long departmentId;

    @Size(max = 32)
    @Pattern(regexp = ValidationPatterns.IDENTIFIER_CODE,
             message = ValidationPatterns.IDENTIFIER_CODE_MESSAGE)
    @Schema(description = "SENSITIVE - changing this pauses the pass")
    private String rollNo;

    @Pattern(regexp = "^$|^\\d{12}$", message = "Government ID must be 12 digits")
    @Schema(description = "SENSITIVE - changing this pauses the pass")
    private String govId;

    @Size(max = 250)
    private String address;

    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Schema(description = "SENSITIVE - changing this pauses the pass")
    private String photoS3Key;
}
