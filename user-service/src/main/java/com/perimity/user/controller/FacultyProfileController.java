package com.perimity.user.controller;

import com.perimity.user.dto.ApiResponse;
import com.perimity.user.dto.request.FacultyProfileCreateDto;
import com.perimity.user.dto.request.FacultyProfileUpdateDto;
import com.perimity.user.dto.response.FacultyProfileResponse;
import com.perimity.user.dto.response.PageResponse;
import com.perimity.user.security.CurrentUser;
import com.perimity.user.service.FacultyProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Faculty profiles.
 *
 * The list endpoint is open to any signed-in user on the campus, unlike the
 * student directory. A visitor filling in a request has to pick the person they
 * are coming to see, so hiding the faculty list would make visitor
 * self-service impossible. Nothing sensitive is in the response - name lives in
 * auth-service, and there is no government id on a faculty profile.
 */
@RestController
@RequestMapping("/api/user/faculty")
@Validated
@Tag(name = "Faculty profiles", description = "Faculty identity records and the host list visitors pick from")
public class FacultyProfileController {

    private final FacultyProfileService facultyProfileService;
    private final CurrentUser currentUser;

    public FacultyProfileController(FacultyProfileService facultyProfileService,
                                    CurrentUser currentUser) {
        this.facultyProfileService = facultyProfileService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Create a faculty profile for an existing account")
    public ResponseEntity<ApiResponse<FacultyProfileResponse>> create(
            @Valid @RequestBody FacultyProfileCreateDto dto) {

        FacultyProfileResponse created = facultyProfileService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Faculty profile created", created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one faculty profile")
    public ApiResponse<FacultyProfileResponse> getOne(@PathVariable @Positive Long id) {
        return ApiResponse.ok(facultyProfileService.getOne(id));
    }

    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Get the faculty profile attached to an account")
    public ApiResponse<FacultyProfileResponse> getByUser(@PathVariable @Positive Long userId) {
        return ApiResponse.ok(facultyProfileService.getByUserId(userId));
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user's own faculty profile")
    public ApiResponse<FacultyProfileResponse> me() {
        return ApiResponse.ok(facultyProfileService.getByUserId(currentUser.userId()));
    }

    @GetMapping
    @Operation(summary = "Faculty on a campus. Any signed-in user may read this - visitors pick a host from it.")
    public ApiResponse<PageResponse<FacultyProfileResponse>> list(
            @RequestParam(required = false) @Positive Long campusId,
            @RequestParam(required = false) @Positive Long departmentId,
            // No sort here on purpose. The repository method already ends in
            // OrderByIdDesc; adding a Sort as well makes Spring Data emit both
            // orderings into one ORDER BY clause.
            @PageableDefault(size = 20) Pageable pageable) {

        Long scope = currentUser.resolveCampusForListing(campusId);
        return ApiResponse.ok(facultyProfileService.list(scope, departmentId, pageable));
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "How many faculty are on this campus")
    public ApiResponse<Map<String, Long>> count(
            @RequestParam(required = false) @Positive Long campusId) {

        Long scope = currentUser.resolveCampusForListing(campusId);
        return ApiResponse.ok(Map.of("count", facultyProfileService.countByCampus(scope)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a faculty profile. Changing a sensitive field pauses the holder's pass.")
    public ApiResponse<FacultyProfileResponse> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody FacultyProfileUpdateDto dto) {

        return ApiResponse.ok("Faculty profile updated", facultyProfileService.update(id, dto));
    }
}
