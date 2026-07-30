package com.perimity.campus.dto.request;

import com.perimity.campus.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PUT /api/campus/campuses/{id}
 *
 * code is deliberately absent. It is baked into storage prefixes, URLs and log
 * lines the moment the campus is created, so renaming it would orphan every
 * existing object. If a code is genuinely wrong, the campus is recreated.
 *
 * active is also absent - see CampusStatusUpdateDto, which forces a reason.
 */
@Schema(description = "Edit a campus. The campus code cannot be changed.")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampusUpdateDto {

    @NotBlank(message = "Campus name is required")
    @Size(max = 150)
    @Pattern(regexp = ValidationPatterns.DISPLAY_NAME, message = ValidationPatterns.DISPLAY_NAME_MESSAGE)
    private String name;

    @Size(max = 250)
    private String address;

    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Size(max = 180)
    private String contactEmail;

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    private String contactPhone;

    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    private String logoS3Key;

    @Positive(message = "Admin user id must be a positive number")
    @Schema(description = "Reassign the Campus Admin account", nullable = true)
    private Long adminUserId;
}
