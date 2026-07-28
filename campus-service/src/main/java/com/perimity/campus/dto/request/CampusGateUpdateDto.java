package com.perimity.campus.dto.request;

import com.perimity.campus.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PUT /api/campus/gates/{id}
 *
 * campusId is absent: a gate cannot be moved to a different campus. Deleting
 * and recreating is correct there, because scan history is tied to the gate.
 *
 * Deactivating a gate is low-risk and reversible - unlike deactivating a whole
 * campus - so it rides along here rather than needing its own endpoint.
 */
@Schema(description = "Edit a gate, or take it out of service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampusGateUpdateDto {

    @NotBlank(message = "Gate name is required")
    @Size(max = 100)
    @Pattern(regexp = ValidationPatterns.DISPLAY_NAME, message = ValidationPatterns.DISPLAY_NAME_MESSAGE)
    private String name;

    @Size(max = 150)
    private String location;

    @NotNull(message = "Active state is required")
    @Schema(description = "false takes the gate out of service; existing scan history is kept",
            example = "true")
    private Boolean active;
}
