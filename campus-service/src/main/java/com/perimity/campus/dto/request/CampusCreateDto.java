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
 * Body of POST /api/campus/campuses - Super Admin onboards an institution.
 *
 * "Is this code already taken" is a database question, not a regex one, so the
 * service layer answers it with CampusRepository.existsByCodeIgnoreCase.
 *
 * active is absent on purpose: a new campus is always created active, and
 * deactivating it later is a separate, deliberate action.
 */
@Schema(description = "Onboard a new institution onto the platform")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampusCreateDto {

    @NotBlank(message = "Campus code is required")
    @Size(max = 32)
    @Pattern(regexp = ValidationPatterns.CAMPUS_CODE, message = ValidationPatterns.CAMPUS_CODE_MESSAGE)
    @Schema(description = "Stable url-safe handle, also used as the storage prefix. "
            + "Chosen once and never changed.", example = "north-campus")
    private String code;

    @NotBlank(message = "Campus name is required")
    @Size(max = 150)
    @Pattern(regexp = ValidationPatterns.DISPLAY_NAME, message = ValidationPatterns.DISPLAY_NAME_MESSAGE)
    @Schema(example = "North Campus Institute of Technology")
    private String name;

    @Size(max = 250)
    @Schema(example = "12 Ring Road, Sector 4")
    private String address;

    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Size(max = 180)
    @Schema(example = "office@north-campus.example.com")
    private String contactEmail;

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    @Schema(example = "+919876543210")
    private String contactPhone;

    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Schema(description = "Storage key for the logo, never the image bytes",
            example = "campuses/north-campus/logo.png")
    private String logoS3Key;

    @Positive(message = "Admin user id must be a positive number")
    @Schema(description = "Campus Admin account in auth-service. May be assigned later.",
            nullable = true, example = "7")
    private Long adminUserId;
}
