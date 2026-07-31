package com.perimity.campus.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PUT /api/campus/campuses/{campusId}/config
 *
 * Used when a Campus Admin saves the whole settings screen at once, and when a
 * new campus is seeded with its default policy set.
 *
 * The @Valid on the list is what makes the nested rules run. Without it the
 * list is accepted whole and every CampusConfigUpsertDto inside goes unchecked -
 * a quiet and very common mistake.
 */
@Schema(description = "Create or replace several per-campus settings in one call")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampusConfigBulkUpsertDto {

    @NotEmpty(message = "At least one setting is required")
    @Size(max = 200, message = "No more than 200 settings may be sent in one call")
    @Valid
    private List<CampusConfigUpsertDto> settings;
}
