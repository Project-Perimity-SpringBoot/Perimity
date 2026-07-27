package com.perimity.gatepass.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.perimity.gatepass.entity.enums.RequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PATCH /api/gatepass/visitor-requests/{id}/decision
 *
 * An APPROVED decision is what creates the GatePass row (status = PENDING).
 */
@Schema(description = "Faculty or Campus Admin approves or rejects a visitor request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitorRequestDecisionDto {

    @NotNull(message = "A decision is required")
    @Schema(description = "APPROVED or REJECTED only. PENDING and CANCELLED are not decisions.",
            example = "APPROVED")
    private RequestStatus decision;

    @NotNull(message = "Reviewer is required")
    @Positive(message = "Reviewer user id must be a positive number")
    @Schema(example = "42")
    private Long reviewedBy;

    @Size(max = 500)
    @Schema(description = "Mandatory when the decision is REJECTED")
    private String rejectReason;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "decision must be APPROVED or REJECTED")
    public boolean isDecisionAllowed() {
        return decision == null
                || decision == RequestStatus.APPROVED
                || decision == RequestStatus.REJECTED;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "rejectReason is required when rejecting a request")
    public boolean isRejectReasonPresentWhenRejected() {
        if (decision != RequestStatus.REJECTED) {
            return true;
        }
        return rejectReason != null && !rejectReason.isBlank();
    }
}
