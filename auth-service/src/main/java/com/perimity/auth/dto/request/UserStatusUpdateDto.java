package com.perimity.auth.dto.request;

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
 * Body of PATCH /api/auth/users/{id}/status
 *
 * Nothing in Perimity is ever hard-deleted - accounts are deactivated. This
 * endpoint is how, and the mandatory reason is what makes the resulting
 * ACCOUNT_DEACTIVATED audit row worth reading a year later.
 *
 * Deactivating a Faculty account should also make the service layer consider
 * their pending approvals; deactivating a holder should pause their passes.
 */
@Schema(description = "Activate or deactivate an account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatusUpdateDto {

    @NotNull(message = "Target state is required")
    @Schema(example = "false")
    private Boolean active;

    @NotBlank(message = "A reason is required for every status change")
    @Size(min = 3, max = 500)
    @Schema(example = "Staff member has left the institution")
    private String reason;

    @NotNull(message = "The user making the change is required")
    @Positive(message = "User id must be a positive number")
    private Long changedBy;
}
