package com.perimity.user.controller;

import com.perimity.user.dto.ApiResponse;
import com.perimity.user.dto.request.ProfileSummaryBatchRequest;
import com.perimity.user.dto.response.ProfileSummaryBatchResponse;
import com.perimity.user.dto.response.ProfileSummaryResponse;
import com.perimity.user.service.ProfileLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service reads. Guarded by X-Internal-Api-Key, never public.
 *
 * =========================================================
 *  THE PATH AND RESPONSE SHAPE HERE ARE A FIXED CONTRACT
 * =========================================================
 *
 * gatepass-service already calls this. From its InternalServiceClient:
 *
 *     GET /api/user/internal/profiles/{userId}/summary
 *     -> { "success": true, "data": { userId, identifierCode, photoS3Key } }
 *
 * It reads three fields out of the envelope. This endpoint returns more, which
 * is fine because Spring Boot's Jackson ignores unknown properties. What is NOT
 * fine is renaming or removing any of those three, or changing the envelope:
 * the call would start returning nulls and every printed pass would silently
 * lose its photo with nothing in a log to explain it.
 *
 * READ ONLY, on purpose. No service should be able to CHANGE another's data
 * through a shared key - a leaked key would then be a compromised platform
 * rather than a compromised report.
 *
 * CALLERS:
 *   gatepass-service  identifier + photo key, to build the QR job (Day 8)
 *   gatepass bulk     the batch lookup, so 600 rows is one call (Day 10)
 *   guard-service     photoUrl, to put a face on the scanner screen (Day 11)
 */
@RestController
@RequestMapping("/api/user/internal")
@Validated
@Tag(name = "Internal", description = "Service-to-service reads. Not for browsers.")
public class UserInternalController {

    private final ProfileLookupService profileLookupService;

    public UserInternalController(ProfileLookupService profileLookupService) {
        this.profileLookupService = profileLookupService;
    }

    /**
     * One profile, with a signed photo link.
     *
     * THE SCANNER CALL (Day 11). photoUrl is included here rather than behind a
     * second endpoint because guard-service makes this call while a person is
     * standing at a gate, and the plan targets a result in under two seconds.
     * Two round trips to fetch one face is the wrong trade at a barrier.
     *
     * The link is short-lived and minted per request. In S3 mode it is absolute
     * and carries its own signature, so the scanner UI can use it directly. In
     * local development mode it is a relative path served by
     * LocalStorageController, which the guard's own browser can fetch because
     * the guard is signed in - guard-SERVICE holds only an API key, but the
     * scanner SCREEN holds a GUARD-role JWT.
     */
    @GetMapping("/profiles/{userId}/summary")
    @Operation(summary = "Identifier, campus and a short-lived photo link for one account")
    public ApiResponse<ProfileSummaryResponse> summary(@PathVariable @Positive Long userId) {
        return ApiResponse.ok(profileLookupService.summaryOf(userId));
    }

    /**
     * Many profiles in one call (Day 10).
     *
     * A bulk upload is up to a thousand rows. Enriching each one through the
     * single endpoint above is a thousand HTTP round trips for something two
     * queries answer.
     *
     * POST rather than GET because a thousand ids in a query string is about
     * 8 KB of URL, which is Tomcat's default header limit - it would fail on
     * exactly the large batches that matter and not on the small ones anybody
     * tests with. A read expressed as a POST, deliberately.
     *
     * Accounts with no profile come back in `missing` rather than as an error.
     * Bulk-created VISITOR identities legitimately have no profile here, so a
     * mixed sheet is SUPPOSED to return fewer than it was asked about.
     */
    @PostMapping("/profiles/summaries")
    @Operation(summary = "Resolve up to 1000 accounts to their profiles. Misses are data, not errors.")
    public ApiResponse<ProfileSummaryBatchResponse> summaries(
            @Valid @RequestBody ProfileSummaryBatchRequest request,
            @RequestParam(defaultValue = "false") boolean withPhotoUrl) {

        return ApiResponse.ok(
                profileLookupService.summariesOf(request.getUserIds(), withPhotoUrl));
    }

    /**
     * Lets a caller check a profile exists without pulling one back.
     *
     * Cheaper than fetching and discarding a body, and it keeps "does this
     * person have a profile" out of the 404 path, where a perfectly normal
     * answer would otherwise fill the logs with warnings.
     */
    @GetMapping("/profiles/{userId}/exists")
    @Operation(summary = "Does this account have a profile in user-service?")
    public ApiResponse<Map<String, Boolean>> exists(@PathVariable @Positive Long userId) {
        return ApiResponse.ok(Map.of("exists", profileLookupService.hasProfile(userId)));
    }
}
