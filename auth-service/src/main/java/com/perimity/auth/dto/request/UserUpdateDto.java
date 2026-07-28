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
 * Body of PUT /api/auth/users/{id}
 *
 * email, role and campusId are all absent, and that is the point of this DTO.
 *
 * email is the universal key that ties one person together across all six
 * services - changing it would orphan their profile, their passes and their
 * entry history at once.
 *
 * role and campusId are privilege. If they were editable here, one careless
 * PUT would turn a Student into a Super Admin. Both need their own audited
 * endpoints.
 */
@Schema(description = "Edit a user's contact details. Email, role and campus cannot be changed here.")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateDto {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 120)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
    private String name;

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    private String phone;
}
