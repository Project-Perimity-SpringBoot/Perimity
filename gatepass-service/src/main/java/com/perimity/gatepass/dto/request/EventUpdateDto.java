package com.perimity.gatepass.dto.request;

import com.perimity.gatepass.validation.ValidDateRange;
import com.perimity.gatepass.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PUT /api/gatepass/events/{id}
 *
 * campusId and createdBy are deliberately absent - an event cannot be moved to
 * another campus or reassigned to another creator.
 *
 * No @FutureOrPresent here, on purpose: an event that has already started must
 * still be editable, for example to extend it by a day.
 */
@Schema(description = "Edit an existing event")
@ValidDateRange(from = "validFrom", to = "validTo",
        message = "The event end date cannot be before the start date")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventUpdateDto {

    @NotBlank(message = "Event name is required")
    @Size(min = 3, max = 180)
    @Pattern(regexp = ValidationPatterns.TITLE, message = ValidationPatterns.TITLE_MESSAGE)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull(message = "Event start date is required")
    private LocalDate validFrom;

    @NotNull(message = "Event end date is required")
    private LocalDate validTo;
}
