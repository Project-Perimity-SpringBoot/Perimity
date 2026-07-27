package com.perimity.gatepass.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.perimity.gatepass.entity.enums.PassType;
import com.perimity.gatepass.validation.ValidDateRange;
import com.perimity.gatepass.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/gatepass/passes
 *
 * status, qrKey, pdfKey and every revoke/pause field are owned by the service
 * layer and the QR pipeline, so none of them appear here.
 *
 * endNullable = true because a standing DAILY pass legitimately has no end date.
 */
@Schema(description = "Issue a gate pass. A DAILY pass may have no end date; an EVENT pass must have one.")
@ValidDateRange(from = "validFrom", to = "validTo", endNullable = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatePassCreateDto {

    @NotNull(message = "Pass holder is required")
    @Positive(message = "Holder user id must be a positive number")
    @Schema(example = "108")
    private Long holderUserId;

    @NotBlank(message = "Holder name is required")
    @Size(min = 2, max = 120)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
    @Schema(description = "Copied onto the pass at issue time so the QR job never calls auth-service",
            example = "Rohit Kulkarni")
    private String holderName;

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(example = "1")
    private Long campusId;

    @Positive(message = "Visitor request id must be a positive number")
    @Schema(description = "Set when this pass came from an approved visitor request", nullable = true)
    private Long visitorRequestId;

    @NotNull(message = "Pass type is required")
    @Schema(description = "DAILY or EVENT", example = "DAILY")
    private PassType passType;

    @Positive(message = "Event id must be a positive number")
    @Schema(description = "Required for an EVENT pass, must be empty for a DAILY pass", nullable = true)
    private Long eventId;

    @NotNull(message = "Start of validity is required")
    @Schema(example = "2026-08-10")
    private LocalDate validFrom;

    @Schema(description = "Leave empty for a standing DAILY pass. Mandatory for an EVENT pass.",
            nullable = true, example = "2026-08-12")
    private LocalDate validTo;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "An EVENT pass requires both an eventId and a validTo date")
    public boolean isEventPassComplete() {
        if (passType != PassType.EVENT) {
            return true;
        }
        return eventId != null && validTo != null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "A DAILY pass must not carry an eventId")
    public boolean isDailyPassFreeOfEvent() {
        if (passType != PassType.DAILY) {
            return true;
        }
        return eventId == null;
    }
}
