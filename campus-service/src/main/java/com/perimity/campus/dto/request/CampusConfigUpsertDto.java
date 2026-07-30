package com.perimity.campus.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.perimity.campus.entity.enums.ConfigValueType;
import com.perimity.campus.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
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
 * Body of PUT /api/campus/campuses/{campusId}/config/{key}
 *
 * campus_config is a key-value store, so the schema cannot enforce that
 * "approval.required" holds a boolean and not the word "banana". This DTO does
 * that job instead: valueType declares how the text should be read, and the
 * @AssertTrue below checks the text actually parses that way.
 *
 * Without this check a typo silently poisons a setting that five other services
 * read, and the failure surfaces days later at a gate.
 */
@Schema(description = "Create or replace one per-campus setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampusConfigUpsertDto {

    @NotBlank(message = "Config key is required")
    @Size(max = 100)
    @Pattern(regexp = ValidationPatterns.CONFIG_KEY, message = ValidationPatterns.CONFIG_KEY_MESSAGE)
    @Schema(example = "approval.required")
    private String configKey;

    @Size(max = 2000)
    @Schema(description = "Always sent as text. valueType says how to read it.", example = "true")
    private String configValue;

    @NotNull(message = "Value type is required")
    @Schema(description = "STRING, BOOLEAN, INTEGER or JSON", example = "BOOLEAN")
    private ConfigValueType valueType;

    @Size(max = 200)
    @Schema(example = "Whether a visitor request needs faculty approval before a pass is issued")
    private String description;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "configValue does not match the declared valueType")
    public boolean isValueParseableAsDeclaredType() {
        if (valueType == null || configValue == null || configValue.isBlank()) {
            return true;
        }
        String v = configValue.trim();
        return switch (valueType) {
            case STRING -> true;
            case BOOLEAN -> v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false");
            case INTEGER -> v.matches("^-?\\d{1,18}$");
            // Shape check only. The service layer should do a real Jackson parse
            // before saving - this catches the common typo, not a malformed body.
            case JSON -> (v.startsWith("{") && v.endsWith("}"))
                      || (v.startsWith("[") && v.endsWith("]"));
        };
    }
}
