package com.perimity.gatepass.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.perimity.gatepass.entity.enums.PassType;
import com.perimity.gatepass.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/gatepass/bulk/validate - phase one of the two-phase flow.
 *
 * There is one bulk engine, not two. passType is the only thing that differs:
 *   DAILY - student onboarding batch, no end date, no event
 *   EVENT - event visitor batch, dates taken from the event, not from the rows
 */
@Schema(description = "Start a bulk upload batch and run the fast synchronous validation pass")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkUploadInitDto {

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(example = "1")
    private Long campusId;

    @NotNull(message = "Uploader is required")
    @Positive(message = "Uploader user id must be a positive number")
    @Schema(example = "42")
    private Long uploadedBy;

    @NotNull(message = "Batch type is required")
    @Schema(description = "DAILY for a student batch, EVENT for an event visitor batch",
            example = "EVENT")
    private PassType passType;

    @Positive(message = "Event id must be a positive number")
    @Schema(description = "Required when passType is EVENT. Supplies the date range for every row.",
            nullable = true)
    private Long eventId;

    @NotBlank(message = "Object storage key of the uploaded sheet is required")
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Schema(example = "campus-1/bulk/2026-08-01-ai-summit.xlsx")
    private String objectKey;

    @NotBlank(message = "Original file name is required")
    @Pattern(regexp = ValidationPatterns.SPREADSHEET_FILENAME,
             message = ValidationPatterns.SPREADSHEET_FILENAME_MESSAGE)
    @Schema(example = "ai-summit-attendees.xlsx")
    private String originalFilename;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "An EVENT batch must be linked to an eventId")
    public boolean isEventPresentForEventBatch() {
        if (passType != PassType.EVENT) {
            return true;
        }
        return eventId != null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "A DAILY student batch must not carry an eventId")
    public boolean isDailyBatchFreeOfEvent() {
        if (passType != PassType.DAILY) {
            return true;
        }
        return eventId == null;
    }
}
