package com.perimity.guard.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * Body of POST /api/guard/scan - the whole product, in one request.
 *
 * NOTE WHAT IS NOT HERE: gateId, campusId, scannedAt, and any result field.
 *
 *   gateId and campusId come from the guard's OPEN SESSION. If the client could
 *   name the gate, a guard could log entries at a gate they were never posted
 *   to, and the entry log would stop being evidence - which is the only thing
 *   making it better than the notebook it replaced.
 *
 *   scannedAt is stamped by the server. A client-controlled scan time can be
 *   back-dated.
 *
 *   scanResult and denialReason are decided by the scan logic. A scanner that
 *   could declare its own result could wave anyone through.
 *
 * Entry only. There is no direction field and no in/out toggle, by design.
 */
@Schema(description = "A guard scans a QR pass at the gate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanRequestDto {

    @NotBlank(message = "The scanned token is required")
    @Size(max = 2048, message = "Token is too long to have come from a QR code")
    @Pattern(regexp = "^[A-Za-z0-9+/=_.:-]+$",
             message = "Token contains characters that could not have come from a Perimity QR code")
    @Schema(description = "The payload read out of the QR code, exactly as scanned")
    private String token;

    @NotNull(message = "Guard is required")
    @Positive(message = "Guard user id must be a positive number")
    @Schema(description = "Used to find the open session, which supplies gate and campus",
            example = "55")
    private Long guardUserId;

    @Schema(description = "Flat map: userAgent, appVersion, ip")
    private Map<String, Object> deviceInfo;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "deviceInfo must be a flat map of at most 10 short values, "
            + "with no line breaks")
    public boolean isDeviceInfoAcceptable() {
        return DeviceInfoRules.isAcceptable(deviceInfo);
    }
}
