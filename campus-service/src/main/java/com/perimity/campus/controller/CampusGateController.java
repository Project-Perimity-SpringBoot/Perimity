package com.perimity.campus.controller;

import com.perimity.campus.dto.ApiResponse;
import com.perimity.campus.dto.request.CampusGateCreateDto;
import com.perimity.campus.dto.request.CampusGateUpdateDto;
import com.perimity.campus.dto.response.CampusGateResponse;
import com.perimity.campus.service.CampusGateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.perimity.campus.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Physical gates.
 *
 * Nested under the campus on purpose. A gate has no meaning outside one, and
 * the path makes the scoping visible rather than relying on a query parameter
 * everyone must remember to pass.
 */
@RestController
@RequestMapping("/api/campus/campuses/{campusId}/gates")
@Validated
@Tag(name = "Gates", description = "Physical entrances a guard binds to for a shift")
public class CampusGateController {

    private final CampusGateService service;

    public CampusGateController(CampusGateService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Add a gate")
    public ResponseEntity<ApiResponse<CampusGateResponse>> create(
            @PathVariable @Positive Long campusId,
            @Valid @RequestBody CampusGateCreateDto dto) {

        dto.setCampusId(campusId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Gate added", service.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Edit a gate, or take it out of service")
    public ApiResponse<CampusGateResponse> update(
            @PathVariable @Positive Long campusId,
            @PathVariable @Positive Long id,
            @Valid @RequestBody CampusGateUpdateDto dto) {

        return ApiResponse.ok("Gate updated", service.update(campusId, id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One gate")
    public ApiResponse<CampusGateResponse> getOne(
            @PathVariable @Positive Long campusId,
            @PathVariable @Positive Long id) {

        return ApiResponse.ok(service.getOne(campusId, id));
    }

    @GetMapping
    @Operation(summary = "Gates on this campus. Active only unless includeClosed is true.")
    public ApiResponse<List<CampusGateResponse>> list(
            @PathVariable @Positive Long campusId,
            @RequestParam(defaultValue = "false") boolean includeClosed) {

        return ApiResponse.ok(includeClosed ? service.listAll(campusId) : service.listActive(campusId));
    }
}
