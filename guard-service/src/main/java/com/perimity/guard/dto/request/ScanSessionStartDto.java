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

    // guardUserId is NOT here. It comes from the verified JWT (Day 7) - a guard
    // must not be able to open a shift in someone else's name.
    //
    // campusId is NOT here either, for the same reason, though it took longer to
    // notice. It used to be a @NotNull field on this DTO, which meant a guard
    // could open a shift naming ANY campus - and because every scan inherits
    // campus from the open session, that shift could then admit people against
    // another campus's passes and write entry logs into another campus's
    // register.
    //
    // Worse than reading another tenant's data: it writes into it. The register
    // is meant to be evidence, and evidence a guard can file under someone
    // else's institution is not evidence.
    //
    // ScanSessionController already had the right instinct on GET /open -
    // "Campus comes from the token, never a parameter" - it simply was not
    // applied here. It is now.

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
