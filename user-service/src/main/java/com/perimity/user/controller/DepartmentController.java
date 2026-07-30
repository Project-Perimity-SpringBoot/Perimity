package com.perimity.user.controller;

import com.perimity.user.dto.ApiResponse;
import com.perimity.user.dto.request.DepartmentCreateDto;
import com.perimity.user.dto.request.DepartmentUpdateDto;
import com.perimity.user.dto.response.DepartmentResponse;
import com.perimity.user.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * campusId is a request parameter for now. Once Omkar's JWT filter lands (Day
 * 7) it comes from the token instead, and these parameters disappear.
 */
@RestController
@RequestMapping("/api/user/departments")
@Validated
@Tag(name = "Departments", description = "Per-campus department list that profile forms select from")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @PostMapping
    @Operation(summary = "Seed a department for a campus")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(
            @Valid @RequestBody DepartmentCreateDto dto) {

        DepartmentResponse created = departmentService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Department created", created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one department")
    public ApiResponse<DepartmentResponse> getOne(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long campusId) {

        return ApiResponse.ok(departmentService.getOne(campusId, id));
    }

    @GetMapping
    @Operation(summary = "List departments for a campus, alphabetical")
    public ApiResponse<List<DepartmentResponse>> list(
            @RequestParam @Positive Long campusId,
            @RequestParam(defaultValue = "true") boolean activeOnly) {

        return ApiResponse.ok(departmentService.list(campusId, activeOnly));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a department, or retire/restore it from new selections")
    public ApiResponse<DepartmentResponse> update(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long campusId,
            @Valid @RequestBody DepartmentUpdateDto dto) {

        return ApiResponse.ok("Department updated", departmentService.update(campusId, id, dto));
    }
}
