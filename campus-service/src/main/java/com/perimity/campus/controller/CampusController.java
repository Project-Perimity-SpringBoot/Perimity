package com.perimity.campus.controller;

import com.perimity.campus.dto.ApiResponse;
import com.perimity.campus.dto.request.CampusCreateDto;
import com.perimity.campus.dto.request.CampusStatusUpdateDto;
import com.perimity.campus.dto.request.CampusUpdateDto;
import com.perimity.campus.dto.response.CampusResponse;
import com.perimity.campus.dto.response.CampusStatsResponse;
import com.perimity.campus.service.CampusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Institutions.
 *
 * Every request body carries @Valid. Without it none of the DTO constraints run
 * and the endpoint silently accepts anything. @Validated on the class does the
 * same for path variables and request parameters.
 *
 * Day 7: creating and deactivating a campus is Super Admin only; editing is
 * Super Admin or that campus's own admin. Add @PreAuthorize once Omkar's filter
 * lands - do not guess his config before then.
 */
@RestController
@RequestMapping("/api/campus/campuses")
@Validated
@Tag(name = "Campuses", description = "Onboard and manage institutions")
public class CampusController {

    private final CampusService service;

    public CampusController(CampusService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Onboard an institution. Seeds its default settings.")
    public ResponseEntity<ApiResponse<CampusResponse>> create(
            @Valid @RequestBody CampusCreateDto dto) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Campus created", service.create(dto)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a campus. The code cannot be changed.")
    public ApiResponse<CampusResponse> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody CampusUpdateDto dto) {

        return ApiResponse.ok("Campus updated", service.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate an entire campus. Requires a reason.")
    public ApiResponse<CampusResponse> changeStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody CampusStatusUpdateDto dto) {

        return ApiResponse.ok("Campus status changed", service.changeStatus(id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One campus")
    public ApiResponse<CampusResponse> getOne(@PathVariable @Positive Long id) {
        return ApiResponse.ok(service.getOne(id));
    }

    @GetMapping("/by-code/{code}")
    @Operation(summary = "Look up by code. This is how other services resolve a campus.")
    public ApiResponse<CampusResponse> getByCode(@PathVariable @NotBlank String code) {
        return ApiResponse.ok(service.getByCode(code));
    }

    @GetMapping
    @Operation(summary = "List campuses. Active only unless includeInactive is true.")
    public ApiResponse<List<CampusResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {

        return ApiResponse.ok(includeInactive ? service.listAll() : service.listActive());
    }

    @GetMapping("/stats")
    @Operation(summary = "Platform counts for the Super Admin dashboard")
    public ApiResponse<CampusStatsResponse> stats() {
        return ApiResponse.ok(service.stats());
    }
}
