package com.perimity.gatepass.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.perimity.gatepass.entity.enums.PassStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PATCH /api/gatepass/passes/{id}/status
 *
 * This DTO only checks that the requested target is one a human is allowed to
 * ask for. Whether the move is legal from the pass's CURRENT state is a
 * database question, so the service layer answers it with
 * PassStatus.canTransitionTo(target).
 *
 * PENDING is set at creation and EXPIRED is set by the scheduled sweep, so
 * neither can be requested through the API.
 */
@Schema(description = "Pause, resume or revoke a pass")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatePassStatusUpdateDto {

    @NotNull(message = "Target status is required")
    @Schema(description = "ACTIVE, PAUSED or REVOKED", example = "PAUSED")
    private PassStatus targetStatus;

    @NotBlank(message = "A reason is required for every status change")
    @Size(min = 3, max = 500)
    @Schema(example = "Aadhaar number was edited, pass held for re-approval")
    private String reason;

    /**
     * SERVER-OWNED since Day 7. Taken from the JWT by the controller, never
     * from the request body.
     *
     * @JsonIgnore is the important part: without it a caller could put their
     * own value here and the controller's overwrite would be the only thing
     * stopping them. With it, Jackson discards the key and the field cannot be
     * injected at all.
     *
     * No @NotNull either - validation runs BEFORE the controller sets it, so a
     * constraint here would reject every request.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Schema(hidden = true)
    private Long changedBy;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "targetStatus must be ACTIVE, PAUSED or REVOKED. "
            + "PENDING is set at creation and EXPIRED is set automatically.")
    public boolean isTargetStatusRequestable() {
        return targetStatus == null
                || targetStatus == PassStatus.ACTIVE
                || targetStatus == PassStatus.PAUSED
                || targetStatus == PassStatus.REVOKED;
    }
}
