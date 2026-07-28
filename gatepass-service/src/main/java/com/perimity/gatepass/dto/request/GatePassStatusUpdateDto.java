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

    @NotNull(message = "The user making the change is required")
    @Positive(message = "User id must be a positive number")
    @Schema(example = "42")
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
