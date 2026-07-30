package com.perimity.user.controller;

import com.perimity.user.dto.ApiResponse;
import com.perimity.user.dto.request.StudentProfileCreateDto;
import com.perimity.user.dto.request.StudentProfileUpdateDto;
import com.perimity.user.dto.response.PageResponse;
import com.perimity.user.dto.response.StudentProfileResponse;
import com.perimity.user.security.CurrentUser;
import com.perimity.user.service.StudentProfileService;
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
 * Student profiles.
 *
 * Every request body carries @Valid. Without it none of the constraints on the
 * DTOs run at all, and the failure is silent - the endpoint simply accepts
 * everything. @Validated on the class does the same job for path variables and
 * request parameters.
 *
 * Nothing here returns an entity. StudentProfile carries the government id in
 * full; StudentProfileResponse returns it masked. Returning the entity would
 * put twelve real digits into every list response and into the browser's
 * network tab.
 *
 * ==========================================================
 *  DAY 7: campusId no longer arrives as a request parameter
 * ==========================================================
 *
 * It comes from the JWT. The one exception is ?campusId= on the list endpoint,
 * which ONLY a Super Admin may use - they have no campus of their own and must
 * name one. CurrentUser.resolveCampusForListing refuses it for everyone else.
 *
 * Role annotations answer "may this KIND of user call this endpoint". They
 * cannot answer "whose record may they touch" - that check lives in the service
 * layer, which is the only place that knows who owns the row.
 */
@RestController
@RequestMapping("/api/user/students")
@Validated
@Tag(name = "Student profiles", description = "Student identity records. The login account lives in auth-service.")
public class StudentProfileController {

    private final StudentProfileService studentProfileService;
    private final CurrentUser currentUser;

    public StudentProfileController(StudentProfileService studentProfileService,
                                    CurrentUser currentUser) {
        this.studentProfileService = studentProfileService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Create a student profile for an existing account")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> create(
            @Valid @RequestBody StudentProfileCreateDto dto) {

        StudentProfileResponse created = studentProfileService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Student profile created", created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one student profile. A student may read only their own.")
    public ApiResponse<StudentProfileResponse> getOne(@PathVariable @Positive Long id) {
        return ApiResponse.ok(studentProfileService.getOne(id));
    }

    /**
     * The lookup the frontend actually uses. A token carries an account id, not
     * a profile id, so "my profile" would otherwise need a search first.
     */
    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Get the student profile attached to an account")
    public ApiResponse<StudentProfileResponse> getByUser(@PathVariable @Positive Long userId) {
        return ApiResponse.ok(studentProfileService.getByUserId(userId));
    }

    /** Shorthand for the signed-in student, so the shell never needs its own id. */
    @GetMapping("/me")
    @Operation(summary = "The signed-in user's own student profile")
    public ApiResponse<StudentProfileResponse> me() {
        return ApiResponse.ok(studentProfileService.getByUserId(currentUser.userId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Student directory for a campus, newest first, optionally by department")
    public ApiResponse<PageResponse<StudentProfileResponse>> list(
            @RequestParam(required = false) @Positive Long campusId,
            @RequestParam(required = false) @Positive Long departmentId,
            // No sort here on purpose. The repository method already ends in
            // OrderByIdDesc; adding a Sort as well makes Spring Data emit both
            // orderings into one ORDER BY clause.
            @PageableDefault(size = 20) Pageable pageable) {

        Long scope = currentUser.resolveCampusForListing(campusId);
        return ApiResponse.ok(studentProfileService.list(scope, departmentId, pageable));
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "How many students are on this campus")
    public ApiResponse<Map<String, Long>> count(
            @RequestParam(required = false) @Positive Long campusId) {

        Long scope = currentUser.resolveCampusForListing(campusId);
        return ApiResponse.ok(Map.of("count", studentProfileService.countByCampus(scope)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a student profile. Changing a sensitive field pauses the holder's pass.")
    public ApiResponse<StudentProfileResponse> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody StudentProfileUpdateDto dto) {

        return ApiResponse.ok("Student profile updated", studentProfileService.update(id, dto));
    }
}
