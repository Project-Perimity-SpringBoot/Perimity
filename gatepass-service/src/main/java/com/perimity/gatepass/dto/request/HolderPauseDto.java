package com.perimity.gatepass.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
 * Body of POST /api/gatepass/internal/passes/holder/{holderUserId}/pause
 *
 * SRS v1.1: when a sensitive profile field changes - government id, photo, roll
 * number - every active pass that person holds must be paused until someone
 * re-approves it. A changed photo on a still-active pass is exactly the hole
 * this closes.
 *
 * user-service is the caller, because it is the service that knows a sensitive
 * field was edited.
 *
 * INTERNAL. This is also, deliberately, the only bulk state change in the
 * service - it acts on a person, not on one pass.
 */
@Schema(description = "user-service reports a sensitive profile edit; hold this person's passes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolderPauseDto {

    @NotBlank(message = "A reason is required so the re-approver knows what changed")
    @Size(min = 3, max = 500)
    @Schema(example = "Government ID was changed and needs re-verification")
    private String reason;

    @NotNull(message = "The user who made the edit is required")
    @Positive(message = "User id must be a positive number")
    @Schema(example = "108")
    private Long changedBy;
}
