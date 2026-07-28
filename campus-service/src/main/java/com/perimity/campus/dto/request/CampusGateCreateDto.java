package com.perimity.campus.dto.request;

import com.perimity.campus.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/campus/gates
 *
 * A guard binds to exactly one gate per session and every scan is recorded
 * against the gate it happened at, so a gate must exist before a guard can work.
 *
 * Gate names are unique within a campus - a database question, answered by
 * CampusGateRepository.existsByCampusIdAndNameIgnoreCase, not by this DTO.
 */
@Schema(description = "Add a physical gate to a campus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampusGateCreateDto {

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(example = "1")
    private Long campusId;

    @NotBlank(message = "Gate name is required")
    @Size(max = 100)
    @Pattern(regexp = ValidationPatterns.DISPLAY_NAME, message = ValidationPatterns.DISPLAY_NAME_MESSAGE)
    @Schema(example = "Main Gate")
    private String name;

    @Size(max = 150)
    @Schema(example = "East side, facing the highway")
    private String location;
}
