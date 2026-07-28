package com.perimity.user.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * Body of PATCH /api/user/documents/{id}/verification
 *
 * verifiedBy comes from the authenticated admin, never from the person who
 * uploaded the document. The service layer must take it from the security
 * context and ignore any value a client sends here.
 */
@Schema(description = "An admin marks a document verified, or rejects it")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVerificationDto {

    @NotNull(message = "A verification decision is required")
    @Schema(example = "true")
    private Boolean verified;

    @NotNull(message = "The verifying admin is required")
    @Positive(message = "User id must be a positive number")
    @Schema(example = "7")
    private Long verifiedBy;

    @Size(max = 500)
    @Schema(description = "Mandatory when verified is false, so the person knows what to fix")
    private String remarks;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "remarks are required when rejecting a document")
    public boolean isRemarksPresentWhenRejected() {
        if (verified == null || Boolean.TRUE.equals(verified)) {
            return true;
        }
        return remarks != null && !remarks.isBlank();
    }
}
