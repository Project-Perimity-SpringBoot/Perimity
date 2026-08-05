package com.perimity.user.controller;

import com.perimity.user.dto.ApiResponse;
import com.perimity.user.dto.request.StudentProfileCreateDto;
import com.perimity.user.dto.request.StudentProfileUpdateDto;
import com.perimity.user.dto.request.StudentSelfDetailsDto;
import com.perimity.user.dto.request.StudentVerificationDecisionDto;
import com.perimity.user.dto.response.PageResponse;
import com.perimity.user.dto.response.StudentProfileResponse;
import com.perimity.user.dto.response.PresignedUrlResponse;
import com.perimity.user.security.CurrentUser;
import com.perimity.user.service.ProfileAssetService;
import com.perimity.user.service.StudentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final ProfileAssetService profileAssetService;
    private final CurrentUser currentUser;

    public StudentProfileController(StudentProfileService studentProfileService,
                                    CurrentUser currentUser,
                                    ProfileAssetService profileAssetService) {
        this.studentProfileService = studentProfileService;
        this.currentUser = currentUser;
        this.profileAssetService = profileAssetService;
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

    // =========================================================
    //  SELF-DECLARED DETAILS AND VERIFICATION
    //
    //  Two audiences, deliberately separate paths. Everything under
    //  /me is the student acting on themselves and needs no id in the
    //  URL, because taking the id from the token is the one way it
    //  cannot be swapped for somebody else's.
    // =========================================================

    /**
     * The student filling in their own particulars.
     *
     * No id anywhere - not in the path, not in the body. The account comes from
     * the token, so there is nothing to tamper with. A PUT /students/{id}/details
     * would need an ownership check on every call and would be one forgotten
     * check away from letting any student rewrite another's record.
     *
     * Allowed while DRAFT or REJECTED. Refused while SUBMITTED. Allowed while
     * VERIFIED but clears the verification - see the service.
     */
    @PutMapping("/me/details")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Save your own details",
               description = "Whole-object save. Editing verified details clears the verification "
                       + "and returns them to draft. Refused while faculty are reviewing them.")
    public ApiResponse<StudentProfileResponse> updateOwnDetails(
            @Valid @RequestBody StudentSelfDetailsDto dto) {

        return ApiResponse.ok("Details saved",
                studentProfileService.updateOwnDetails(currentUser.userId(), dto));
    }

    /**
     * Hand the details to faculty.
     *
     * POST rather than PUT: this is not idempotent. It stamps submittedAt, which
     * is what orders the reviewer's queue, and the service refuses a second call
     * so a double click cannot send the student to the back of that queue.
     *
     * No body. The only input is "who is asking", and that is the token.
     */
    @PostMapping("/me/details/submit")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submit your details for verification",
               description = "Everything mandatory must be filled in first. "
                       + "Your details are locked until faculty decide.")
    public ApiResponse<StudentProfileResponse> submitOwnDetails() {
        return ApiResponse.ok("Sent to faculty for checking",
                studentProfileService.submitOwnDetails(currentUser.userId()));
    }

    /**
     * The reviewer's queue - students waiting for a decision, oldest first.
     *
     * GUARD is absent from the role list on purpose. A guard reads a profile at
     * the gate to check a person against it; nothing about that job involves
     * approving the details.
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Students waiting for their details to be checked",
               description = "Oldest submission first, so nobody is left at the back of a backlog.")
    public ApiResponse<PageResponse<StudentProfileResponse>> listPending(
            @RequestParam(required = false) @Positive Long campusId,
            // No Sort parameter: the repository method already ends in
            // OrderBySubmittedAtAsc, and adding one emits two ORDER BY clauses.
            @PageableDefault(size = 20) Pageable pageable) {

        Long scope = currentUser.resolveCampusForListing(campusId);
        return ApiResponse.ok(studentProfileService.listPendingVerification(scope, pageable));
    }

    /** The badge count for the faculty dashboard, without pulling a whole page. */
    @GetMapping("/pending/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "How many students are waiting for a decision")
    public ApiResponse<Map<String, Long>> countPending(
            @RequestParam(required = false) @Positive Long campusId) {

        Long scope = currentUser.resolveCampusForListing(campusId);
        return ApiResponse.ok(
                Map.of("count", studentProfileService.countPendingVerification(scope)));
    }

    /**
     * Accept or refuse a student's submitted details.
     *
     * The body says approved and (on a refusal) why. It does NOT say who is
     * deciding - that comes from the token. StudentVerificationDecisionDto has
     * no verifiedBy field so that a caller cannot claim to be someone else, and
     * so that no future maintainer can start trusting one.
     */
    @PatchMapping("/{id}/verification")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Approve or reject a student's submitted details",
               description = "Only details in the SUBMITTED state can be decided. "
                       + "Remarks are required when rejecting - the student reads them.")
    public ApiResponse<StudentProfileResponse> decideVerification(
            @PathVariable @Positive Long id,
            @Valid @RequestBody StudentVerificationDecisionDto dto) {

        StudentProfileResponse decided = studentProfileService.decideVerification(id, dto);
        return ApiResponse.ok(
                Boolean.TRUE.equals(dto.getApproved()) ? "Details verified" : "Details returned to the student",
                decided);
    }

    // ------------------------------------------------------------- photo

    /**
     * Upload or replace the profile photo (Day 9).
     *
     * multipart/form-data. The storage key is generated server-side from this
     * profile, so an upload can only land under this person's own prefix.
     *
     * THIS PAUSES THE HOLDER'S PASS. The photo is what a guard checks a face
     * against, so changing it while a pass is ACTIVE leaves a QR vouching for a
     * different picture. Same rule as editing a roll number.
     */
    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace the photo. PNG, JPEG or WebP. Pauses the holder's pass.")
    public ApiResponse<StudentProfileResponse> uploadPhoto(
            @PathVariable @Positive Long id,
            @RequestPart("file") MultipartFile file) {

        return ApiResponse.ok("Photo updated. The pass is held pending re-approval.",
                profileAssetService.uploadStudentPhoto(id, file));
    }

    /**
     * A short-lived link to the photo, minted on demand.
     *
     * Not the bytes, and never a permanent URL - see PresignedUrlResponse.
     */
    @GetMapping("/{id}/photo-url")
    @Operation(summary = "Short-lived link to the photo")
    public ApiResponse<PresignedUrlResponse> photoUrl(@PathVariable @Positive Long id) {
        return ApiResponse.ok(profileAssetService.studentPhotoUrl(id));
    }

    @DeleteMapping("/{id}/photo")
    @Operation(summary = "Remove the photo. Also pauses the holder's pass.")
    public ApiResponse<StudentProfileResponse> removePhoto(@PathVariable @Positive Long id) {
        return ApiResponse.ok("Photo removed. The pass is held pending re-approval.",
                profileAssetService.removeStudentPhoto(id));
    }
}
