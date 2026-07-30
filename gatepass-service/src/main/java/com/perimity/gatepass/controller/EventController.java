package com.perimity.gatepass.controller;

import com.perimity.gatepass.dto.ApiResponse;
import com.perimity.gatepass.dto.request.EventCreateDto;
import com.perimity.gatepass.dto.request.EventUpdateDto;
import com.perimity.gatepass.dto.response.EventResponse;
import com.perimity.gatepass.dto.response.PageResponse;
import com.perimity.gatepass.security.CurrentUser;
import com.perimity.gatepass.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Events.
 *
 * campusId is no longer a request parameter. It comes from the JWT, so a caller
 * cannot read or write another institution's events by editing a query string.
 *
 * Reads are open to any authenticated user - a student needs to see what is on.
 * Writes are staff only.
 */
@RestController
@RequestMapping("/api/gatepass/events")
@Validated
@Tag(name = "Events", description = "Programmes that EVENT passes attach to")
public class EventController {

    private final EventService eventService;
    private final CurrentUser currentUser;

    public EventController(EventService eventService, CurrentUser currentUser) {
        this.eventService = eventService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Create an event")
    public ResponseEntity<ApiResponse<EventResponse>> create(
            @Valid @RequestBody EventCreateDto dto) {

        // Overwrite whatever the body claimed. The token is the authority on
        // campus and on who is creating this.
        dto.setCampusId(currentUser.campusId());
        dto.setCreatedBy(currentUser.userId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Event created", eventService.create(dto)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one event")
    public ApiResponse<EventResponse> getOne(@PathVariable @Positive Long id) {
        return ApiResponse.ok(eventService.getOne(currentUser.campusId(), id));
    }

    @GetMapping
    @Operation(summary = "List events on your campus, newest first")
    public ApiResponse<PageResponse<EventResponse>> list(
            @PageableDefault(size = 20, sort = "validFrom", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ApiResponse.ok(eventService.list(currentUser.campusId(), pageable));
    }

    @GetMapping("/running")
    @Operation(summary = "Events live today on your campus")
    public ApiResponse<List<EventResponse>> runningToday() {
        return ApiResponse.ok(eventService.runningToday(currentUser.campusId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Edit an event")
    public ApiResponse<EventResponse> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody EventUpdateDto dto) {

        return ApiResponse.ok("Event updated", eventService.update(currentUser.campusId(), id, dto));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('FACULTY','CAMPUS_ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Cancel an event. Never deletes it. Revokes every pass issued for it.")
    public ApiResponse<EventResponse> cancel(@PathVariable @Positive Long id) {
        // The token is the authority on who cancelled this, for the
        // revokedBy audit field on every pass this revokes.
        return ApiResponse.ok("Event cancelled", eventService.cancel(currentUser.campusId(), id, currentUser.userId()));
    }
}
