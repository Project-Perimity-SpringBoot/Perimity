package com.perimity.user.controller;

import com.perimity.user.dto.ApiResponse;
import com.perimity.user.dto.request.DepartmentCreateDto;
import com.perimity.user.dto.request.DepartmentUpdateDto;
import com.perimity.user.dto.response.DepartmentResponse;
import com.perimity.user.security.CurrentUser;
import com.perimity.user.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Departments - the per-campus list everything else selects from.
 *
 * Every request body carries @Valid. Without it none of the constraints on the
 * DTOs run at all, and the failure is silent - the endpoint simply accepts
 * everything. @Validated on the class does the same job for path variables and
 * request parameters.
 *
 * ==========================================================
 *  DAY 7: the mandatory ?campusId= parameter is gone
 * ==========================================================
 *
 * It used to be required on every read, which meant
 * GET /api/user/departments?campusId=2 returned another campus's departments to
 * anyone who typed a different number. Campus now comes from the JWT.
 *
 * The parameter survives as OPTIONAL for one reason only: a Super Admin has no
 * campus of their own and has to be able to name one. CurrentUser refuses it
 * for every other role, so it grants nothing to anyone else.
 *
 * Reading the list stays open to any signed-in user - a student filling in
 * their own profile needs the dropdown. Only an administrator may change it.
 */
@RestController
@RequestMapping("/api/user/departments")
@Validated
@Tag(name = "Departments", description = "Per-campus department list that profile forms select from")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final CurrentUser currentUser;

    public DepartmentController(DepartmentService departmentService, CurrentUser currentUser) {
        this.departmentService = departmentService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Seed a department for a campus")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(
            @Valid @RequestBody DepartmentCreateDto dto) {

        // The body still carries campusId, so it is checked against the token
        // rather than trusted. A Campus Admin cannot seed a department onto
        // somebody else's campus by editing the JSON.
        currentUser.requireSameCampus(dto.getCampusId());

        DepartmentResponse created = departmentService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Department created", created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one department")
    public ApiResponse<DepartmentResponse> getOne(
            @PathVariable @Positive Long id,
            @RequestParam(required = false) @Positive Long campusId) {

        Long scope = currentUser.resolveCampusForListing(campusId);
        return ApiResponse.ok(departmentService.getOne(scope, id));
    }

    @GetMapping
    @Operation(summary = "List departments for a campus, alphabetical")
    public ApiResponse<List<DepartmentResponse>> list(
            @RequestParam(required = false) @Positive Long campusId,
            @RequestParam(defaultValue = "true") boolean activeOnly) {

        Long scope = currentUser.resolveCampusForListing(campusId);
        return ApiResponse.ok(departmentService.list(scope, activeOnly));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Rename a department, or retire/restore it from new selections")
    public ApiResponse<DepartmentResponse> update(
            @PathVariable @Positive Long id,
            @RequestParam(required = false) @Positive Long campusId,
            @Valid @RequestBody DepartmentUpdateDto dto) {

        Long scope = currentUser.resolveCampusForListing(campusId);
        return ApiResponse.ok("Department updated", departmentService.update(scope, id, dto));
    }
}
