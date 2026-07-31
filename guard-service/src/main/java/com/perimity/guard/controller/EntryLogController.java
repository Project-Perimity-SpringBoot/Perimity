package com.perimity.guard.controller;

import com.perimity.guard.dto.ApiResponse;
import com.perimity.guard.dto.request.EntryLogFilterDto;
import com.perimity.guard.dto.response.EntryLogResponse;
import com.perimity.guard.dto.response.EntryStatsResponse;
import com.perimity.guard.dto.response.EventAttendanceResponse;
import com.perimity.guard.dto.response.PageResponse;
import com.perimity.guard.service.EntryLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Reading the digital gate register.
 *
 * The search endpoint takes its filter as a request body on a POST, not as a
 * dozen query parameters. That is deliberate: the 90-day cap and the
 * range-ordering rule live on the DTO as @AssertTrue, and those only run on a
 * validated body.
 */
@RestController
@RequestMapping("/api/guard/entry-logs")
@Validated
@Tag(name = "Entry log", description = "The searchable replacement for the paper register")
public class EntryLogController {

    private final EntryLogService service;

    public EntryLogController(EntryLogService service) {
        this.service = service;
    }

    @PostMapping("/search")
    @Operation(summary = "Search the register. Range is capped at 90 days.")
    public ApiResponse<PageResponse<EntryLogResponse>> search(
            @Valid @RequestBody EntryLogFilterDto filter,
            @PageableDefault(size = 50) Pageable pageable) {

        return ApiResponse.ok(service.search(filter, pageable));
    }

    @PostMapping("/stats")
    @Operation(summary = "Allowed and denied counts over a range")
    public ApiResponse<EntryStatsResponse> stats(@Valid @RequestBody EntryLogFilterDto filter) {
        return ApiResponse.ok(service.stats(filter));
    }

    @GetMapping("/holder/{holderUserId}")
    @Operation(summary = "One person's movement history")
    public ApiResponse<PageResponse<EntryLogResponse>> byHolder(
            @PathVariable @Positive Long holderUserId,
            @PageableDefault(size = 50) Pageable pageable) {

        return ApiResponse.ok(service.byHolder(holderUserId, pageable));
    }

    @GetMapping("/pass/{passId}")
    @Operation(summary = "Every scan of one pass, refusals included")
    public ApiResponse<List<EntryLogResponse>> byPass(@PathVariable @Positive Long passId) {
        return ApiResponse.ok(service.byPass(passId));
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "Everything scanned during one shift. The handover view.")
    public ApiResponse<List<EntryLogResponse>> bySession(@PathVariable @NotBlank String sessionId) {
        return ApiResponse.ok(service.bySession(sessionId));
    }

    @GetMapping("/events/{eventId}/attendance")
    @Operation(summary = "Organiser attendance. Counted on attributedEventId, so Behavior 2 counts.")
    public ApiResponse<EventAttendanceResponse> attendance(
            @PathVariable @Positive Long eventId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String eventName,
            @RequestParam(defaultValue = "0") @PositiveOrZero long registeredCount) {

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The end date must not be before the start date.");
        }
        if (from.plusDays(90).isBefore(to)) {
            throw new IllegalArgumentException("An event range may not exceed 90 days.");
        }

        return ApiResponse.ok(service.attendance(eventId, eventName, from, to, registeredCount));
    }
}
