package com.perimity.gatepass.controller;

import com.perimity.gatepass.dto.ApiResponse;
import com.perimity.gatepass.dto.request.EventCreateDto;
import com.perimity.gatepass.dto.request.EventUpdateDto;
import com.perimity.gatepass.dto.response.EventResponse;
import com.perimity.gatepass.dto.response.PageResponse;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Events.
 *
 * Every request body carries @Valid. Without it none of the constraints on the
 * DTOs run at all, and the failure is silent - the endpoint simply accepts
 * everything. @Validated on the class does the same job for path variables and
 * request parameters.
 *
 * campusId is a request parameter for now. Once Omkar's JWT filter lands it
 * comes from the token instead, and these parameters disappear.
 */
@RestController
@RequestMapping("/api/gatepass/events")
@Validated
@Tag(name = "Events", description = "Programmes that EVENT passes attach to")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @Operation(summary = "Create an event")
    public ResponseEntity<ApiResponse<EventResponse>> create(
            @Valid @RequestBody EventCreateDto dto) {

        EventResponse created = eventService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Event created", created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one event")
    public ApiResponse<EventResponse> getOne(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long campusId) {

        return ApiResponse.ok(eventService.getOne(campusId, id));
    }

    @GetMapping
    @Operation(summary = "List events for a campus, newest first")
    public ApiResponse<PageResponse<EventResponse>> list(
            @RequestParam @Positive Long campusId,
            @PageableDefault(size = 20, sort = "validFrom", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ApiResponse.ok(eventService.list(campusId, pageable));
    }

    @GetMapping("/running")
    @Operation(summary = "Events live today on this campus")
    public ApiResponse<List<EventResponse>> runningToday(
            @RequestParam @Positive Long campusId) {

        return ApiResponse.ok(eventService.runningToday(campusId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit an event")
    public ApiResponse<EventResponse> update(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long campusId,
            @Valid @RequestBody EventUpdateDto dto) {

        return ApiResponse.ok("Event updated", eventService.update(campusId, id, dto));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel an event. Never deletes it.")
    public ApiResponse<EventResponse> cancel(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long campusId) {

        return ApiResponse.ok("Event cancelled", eventService.cancel(campusId, id));
    }
}
