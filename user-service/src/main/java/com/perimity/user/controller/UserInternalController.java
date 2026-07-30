package com.perimity.user.controller;

import com.perimity.user.dto.ApiResponse;
import com.perimity.user.dto.response.ProfileSummaryResponse;
import com.perimity.user.service.ProfileLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service reads. Guarded by X-Internal-Api-Key, never public.
 *
 * ONE ENDPOINT. That is the whole internal surface of this service, and after
 * the Day 12 backend freeze it should stay that way. A batch lookup and an
 * existence check both lived here and were removed on Day 12 with zero callers
 * between them.
 *
 * =========================================================
 *  THE PATH AND RESPONSE SHAPE ARE A FIXED CONTRACT
 * =========================================================
 *
 * TWO services call this today, and both records read fields by name:
 *
 *   gatepass-service  InternalServiceClient.ProfileView
 *                     (userId, identifierCode, photoS3Key)
 *   guard-service     HttpHolderProfileClient.SummaryView
 *                     (userId, identifierCode, photoS3Key)
 *
 * Adding a field is safe - Jackson drops what a caller does not declare.
 * Renaming or removing one of those three is not: the call keeps returning 200
 * and the field arrives null, so a pass prints without a photo and a scanner
 * shows a blank card, with nothing in any log to explain either.
 *
 * READ ONLY, on purpose. No service should be able to CHANGE another's data
 * through a shared key - a leaked key would then be a compromised platform
 * rather than a compromised report.
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
     * One profile, including a signed photo link.
     *
     * ===================================================================
     *  FOR guard-service: photoUrl IS ALREADY HERE. There is no second
     *  endpoint to wait for, and there should not be one.
     * ===================================================================
     *
     * HttpHolderProfileClient's comment asks for
     * GET /api/user/internal/profiles/{userId}/photo-url. That was the right
     * ask against the Day 9 response, which carried only a storage key. Day 11
     * put the signed link on THIS response instead, so the call guard-service
     * already makes now returns everything the result card needs.
     *
     * Its SummaryView record declares (userId, identifierCode, photoS3Key), so
     * Jackson is currently discarding photoUrl. Adding the field to that record
     * is the whole fix - one line, in guard-service.
     *
     * A separate endpoint would mean a second round trip while a person stands
     * at a gate, against a two-second budget, for data the first call already
     * had.
     *
     * The link is short-lived and minted per request. In S3 mode it is absolute
     * and self-signing. In local development it is a relative path served by
     * LocalStorageController - guard-SERVICE holds only an API key, but the
     * scanner SCREEN is a browser with a GUARD-role JWT, so it can fetch it.
     * Either way the UI just uses the string.
     */
    @GetMapping("/profiles/{userId}/summary")
    @Operation(summary = "Identifier, campus, photo key and a short-lived photo URL for one account")
    public ApiResponse<ProfileSummaryResponse> summary(@PathVariable @Positive Long userId) {
        return ApiResponse.ok(profileLookupService.summaryOf(userId));
    }
}
