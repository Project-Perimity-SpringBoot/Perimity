package com.perimity.campus.dto.request;

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
 * Body of PATCH /api/campus/campuses/{id}/status
 *
 * Deactivating a campus takes an entire institution offline - every gate, every
 * guard session, every pass. It gets its own endpoint and a mandatory reason
 * rather than riding along as a stray boolean in the edit form.
 */
@Schema(description = "Activate or deactivate an entire campus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampusStatusUpdateDto {

    @NotNull(message = "Target state is required")
    @Schema(example = "false")
    private Boolean active;

    @NotBlank(message = "A reason is required for every status change")
    @Size(min = 3, max = 500)
    @Schema(example = "Campus closed for the vacation period")
    private String reason;

    @NotNull(message = "The user making the change is required")
    @Positive(message = "User id must be a positive number")
    @Schema(example = "1")
    private Long changedBy;
}
