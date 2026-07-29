package com.perimity.guard.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.perimity.guard.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/guard/sessions - the guard picks their gate once per shift.
 *
 * This exists so the guard does not choose a gate on every scan. Every scan
 * then inherits gate and campus from the open session, which is faster at the
 * gate and impossible to get wrong halfway through a shift.
 *
 * gateName is denormalised into the session and every entry log so reports
 * render without a call into campus-service.
 */
@Schema(description = "Start a shift by pinning this guard to one gate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanSessionStartDto {

    @NotNull(message = "Guard is required")
    @Positive(message = "Guard user id must be a positive number")
    @Schema(example = "55")
    private Long guardUserId;

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(example = "1")
    private Long campusId;

    @NotNull(message = "Gate is required")
    @Positive(message = "Gate id must be a positive number")
    @Schema(description = "One gate for the whole shift", example = "2")
    private Long gateId;

    @NotBlank(message = "Gate name is required")
    @Size(max = 100)
    @Pattern(regexp = ValidationPatterns.DEVICE_LABEL, message = "Invalid gate name")
    @Pattern(regexp = "^[^\\r\\n\\t]*$", message = "Gate name must not contain line breaks or tabs")
    @Schema(example = "Main Gate")
    private String gateName;

    @Schema(description = "Flat map: userAgent, appVersion, ip. Bounded - see the rules.",
            example = "{\"userAgent\":\"Android 14 / Chrome\",\"appVersion\":\"1.2.0\"}")
    private Map<String, Object> deviceInfo;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "deviceInfo must be a flat map of at most 10 short values, "
            + "with no line breaks")
    public boolean isDeviceInfoAcceptable() {
        return DeviceInfoRules.isAcceptable(deviceInfo);
    }
}
