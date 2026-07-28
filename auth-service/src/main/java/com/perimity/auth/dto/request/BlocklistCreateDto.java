package com.perimity.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * Body of POST /api/auth/blocklist (FR-BLK-1)
 *
 * Blocklist entries are scoped per campus: being barred from one campus does
 * not bar you from another. campusId is therefore mandatory.
 *
 * The reason is mandatory too. An entry with no reason cannot be audited or
 * defended six months later when someone asks why a person was refused entry.
 *
 * The @AssertTrue mirrors BlocklistEntry.isEmailOrPhonePresent so the mistake
 * surfaces as a clean 400 instead of a constraint violation during save.
 */
@Schema(description = "Bar a person from one campus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlocklistCreateDto {

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(description = "A blocklist entry applies to one campus only", example = "1")
    private Long campusId;

    @Size(max = 180)
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    @Schema(nullable = true)
    private String email;

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    @Schema(nullable = true)
    private String phone;

    @NotBlank(message = "A reason is required for every blocklist entry")
    @Size(min = 5, max = 500)
    @Schema(example = "Repeated refusal to follow campus security procedure")
    private String reason;

    @NotNull(message = "The admin adding this entry is required")
    @Positive(message = "User id must be a positive number")
    private Long createdBy;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Provide an email address or a phone number to block")
    public boolean isEmailOrPhonePresent() {
        return (email != null && !email.isBlank()) || (phone != null && !phone.isBlank());
    }
}
