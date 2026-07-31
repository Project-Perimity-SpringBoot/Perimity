package com.perimity.qr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST /api/qr/internal/invalidate/{passId} - called on re-issue or revoke.
 *
 * The reason is mandatory by design. Nothing is ever hard-deleted in Perimity,
 * so an invalidated QrRecord row survives forever as audit evidence; a row
 * saying "deactivated, reason unknown" is evidence of nothing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrInvalidateRequest {

    /**
     * @Size(max = 200) is not arbitrary - it is exactly the length of
     * QrRecord.invalidatedReason's column. Matching them means an overlong
     * reason is a clean 400 listing the field, instead of a
     * DataIntegrityViolationException surfacing as a 409 with no useful
     * detail. Any time a DTO string maps to a column, copy the column length.
     */
    @NotBlank(message = "A reason is required and is kept for audit")
    @Size(max = 200, message = "Reason may be at most 200 characters")
    private String reason;
}
