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
    private final com.perimity.guard.security.CurrentUser currentUser;

    public EntryLogController(EntryLogService service,
                              com.perimity.guard.security.CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /**
     * The single place a campus id enters the read path.
     *
     * ======================================================================
     * WHY A REQUEST NEVER CHOOSES ITS OWN CAMPUS
     * ======================================================================
     * For every role except SUPER_ADMIN this ignores whatever the caller asked
     * for and returns the campus on their verified token. Not "checks it
     * matches" - ignores it. A caller cannot express another campus, so there is
     * no comparison to get wrong and nothing to forget on the next endpoint.
     *
     * SUPER_ADMIN has campusId null by design, so they are the one role that
     * must name a campus, and the only one permitted to. Refusing rather than
     * defaulting is deliberate: silently picking a campus for a platform-wide
     * admin would produce confidently wrong figures.
     */
    private Long resolveCampus(Long requested) {
        if (currentUser.require().isSuperAdmin()) {
            if (requested == null) {
                throw new com.perimity.guard.security.CurrentUser.ForbiddenException(
                        "A Super Admin must name a campus with ?campusId= - the register is "
                        + "read one campus at a time.");
            }
            return requested;
        }
        // Deliberately not compared against `requested`. See above.
        return currentUser.campusId();
    }

    @PostMapping("/search")
    @Operation(summary = "Search the register. Range is capped at 90 days. "
            + "Always scoped to the caller's campus.")
    public ApiResponse<PageResponse<EntryLogResponse>> search(
            @Valid @RequestBody EntryLogFilterDto filter,
            @RequestParam(required = false) Long campusId,
            @PageableDefault(size = 50) Pageable pageable) {

        return ApiResponse.ok(service.search(resolveCampus(campusId), filter, pageable));
    }

    @PostMapping("/stats")
    @Operation(summary = "Allowed, amber and denied counts over a range")
    public ApiResponse<EntryStatsResponse> stats(
            @Valid @RequestBody EntryLogFilterDto filter,
            @RequestParam(required = false) Long campusId) {

        return ApiResponse.ok(service.stats(resolveCampus(campusId), filter));
    }

    @GetMapping("/holder/{holderUserId}")
    @Operation(summary = "One person's movement history, within the caller's campus")
    public ApiResponse<PageResponse<EntryLogResponse>> byHolder(
            @PathVariable @Positive Long holderUserId,
            @RequestParam(required = false) Long campusId,
            @PageableDefault(size = 50) Pageable pageable) {

        return ApiResponse.ok(service.byHolder(resolveCampus(campusId), holderUserId, pageable));
    }

    @GetMapping("/pass/{passId}")
    @Operation(summary = "Every scan of one pass, refusals included")
    public ApiResponse<List<EntryLogResponse>> byPass(
            @PathVariable @Positive Long passId,
            @RequestParam(required = false) Long campusId) {

        return ApiResponse.ok(service.byPass(resolveCampus(campusId), passId));
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "Everything scanned during one shift. The handover view.")
    public ApiResponse<List<EntryLogResponse>> bySession(
            @PathVariable @NotBlank String sessionId,
            @RequestParam(required = false) Long campusId) {

        return ApiResponse.ok(service.bySession(resolveCampus(campusId), sessionId));
    }

    @GetMapping("/events/{eventId}/attendance")
    @Operation(summary = "Organiser attendance. Counted on attributedEventId, so Behavior 2 counts.")
    public ApiResponse<EventAttendanceResponse> attendance(
            @PathVariable @Positive Long eventId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) Long campusId,
            @RequestParam(defaultValue = "0") @PositiveOrZero long registeredCount) {

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The end date must not be before the start date.");
        }
        if (from.plusDays(90).isBefore(to)) {
            throw new IllegalArgumentException("An event range may not exceed 90 days.");
        }

        // FACULTY reach this path and no other entry-log path. An event id is a
        // number they can change in a URL, so the campus term is what stops a
        // lecturer counting another campus's event.
        return ApiResponse.ok(service.attendance(
                resolveCampus(campusId), eventId, eventName, from, to, registeredCount));
    }
}
