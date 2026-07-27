package com.perimity.gatepass.dto.request;

import com.perimity.gatepass.validation.ValidDateRange;
import com.perimity.gatepass.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/gatepass/events
 *
 * "Is there already an event with this name at this campus" is a database
 * question, not a regex one, so the service layer answers it with
 * EventRepository.existsByCampusIdAndNameIgnoreCase.
 */
@Schema(description = "Create a programme that EVENT passes will be attached to")
@ValidDateRange(from = "validFrom", to = "validTo",
        message = "The event end date cannot be before the start date")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventCreateDto {

    @NotNull(message = "Campus is required")
    @Positive(message = "Campus id must be a positive number")
    @Schema(example = "1")
    private Long campusId;

    @NotBlank(message = "Event name is required")
    @Size(min = 3, max = 180)
    @Pattern(regexp = ValidationPatterns.TITLE, message = ValidationPatterns.TITLE_MESSAGE)
    @Schema(example = "AI Summit 2026")
    private String name;

    @Size(max = 1000)
    @Schema(example = "Three-day programme on applied machine learning")
    private String description;

    @NotNull(message = "Event start date is required")
    @FutureOrPresent(message = "An event cannot start in the past")
    @Schema(example = "2026-08-10")
    private LocalDate validFrom;

    @NotNull(message = "Event end date is required")
    @Schema(example = "2026-08-12")
    private LocalDate validTo;

    @NotNull(message = "Creator is required")
    @Positive(message = "Creator user id must be a positive number")
    @Schema(description = "Faculty or Campus Admin creating the event", example = "42")
    private Long createdBy;
}
