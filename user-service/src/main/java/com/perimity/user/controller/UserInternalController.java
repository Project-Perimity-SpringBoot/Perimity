package com.perimity.user.controller;

import com.perimity.user.dto.ApiResponse;
import com.perimity.user.dto.response.ProfileSummaryResponse;
import com.perimity.user.service.ProfileLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
 * It reads three fields out of the envelope. This endpoint returns more than
 * that - campusId, profileType, departmentId - which is fine because Spring
 * Boot's Jackson ignores unknown properties by default. What is NOT fine is
 * renaming or removing any of those three, or changing the envelope: the call
 * would start returning nulls and every printed pass would silently lose its
 * photo with nothing in a log to explain it.
 *
 * READ ONLY, on purpose. No service should be able to CHANGE another's data
 * through a shared key - a leaked key would then be a compromised platform
 * rather than a compromised report.
 *
 * CALLERS TODAY:
 *   gatepass-service  identifier + photo key, to build the QR job (Day 8)
 *   guard-service     the same summary, for the scanner result screen (Day 11)
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

    @GetMapping("/profiles/{userId}/summary")
    @Operation(summary = "Identifier, campus and photo key for one account. Called when issuing a pass.")
    public ApiResponse<ProfileSummaryResponse> summary(@PathVariable @Positive Long userId) {
        return ApiResponse.ok(profileLookupService.summaryOf(userId));
    }

    /**
     * Lets a caller check a profile exists without pulling one back.
     *
     * The bulk engine on Day 10 resolves hundreds of rows by account id; asking
     * a yes/no question is cheaper than fetching and discarding a body, and it
     * keeps "does this person exist here" out of the 404 path, where it would
     * otherwise fill the logs with warnings for a perfectly normal answer.
     */
    @GetMapping("/profiles/{userId}/exists")
    @Operation(summary = "Does this account have a profile in user-service?")
    public ApiResponse<Map<String, Boolean>> exists(@PathVariable @Positive Long userId) {
        return ApiResponse.ok(Map.of("exists", profileLookupService.hasProfile(userId)));
    }
}
