package com.perimity.campus.controller;

import com.perimity.campus.dto.ApiResponse;
import com.perimity.campus.dto.response.CampusResponse;
import com.perimity.campus.security.CurrentUser;
import com.perimity.campus.service.CampusAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Campus logo.
 *
 * A Campus Admin may only touch their own campus; a Super Admin may touch any.
 * That check is CurrentUser.requireSameCampus, not an annotation - a role
 * annotation can say who may call an endpoint, never WHOSE record they may
 * change.
 */
@RestController
@RequestMapping("/api/campus/campuses/{campusId}/logo")
@Validated
@Tag(name = "Campus assets", description = "Logo upload. Files to storage, keys to the database.")
public class CampusAssetController {

    private final CampusAssetService assetService;
    private final CurrentUser currentUser;

    public CampusAssetController(CampusAssetService assetService, CurrentUser currentUser) {
        this.assetService = assetService;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Upload a logo. PNG, JPEG or WebP. Verified by file signature, not filename.")
    public ApiResponse<CampusResponse> upload(@PathVariable @Positive Long campusId,
                                              @RequestParam("file") MultipartFile file) {
        currentUser.requireSameCampus(campusId);
        return ApiResponse.ok("Logo uploaded", assetService.uploadLogo(campusId, file));
    }

    @GetMapping
    @Operation(summary = "A short-lived link to the logo. The bucket itself stays private.")
    public ApiResponse<Map<String, String>> url(@PathVariable @Positive Long campusId) {
        return ApiResponse.ok(Map.of("url", assetService.logoUrl(campusId)));
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Remove the logo")
    public ApiResponse<CampusResponse> remove(@PathVariable @Positive Long campusId) {
        currentUser.requireSameCampus(campusId);
        return ApiResponse.ok("Logo removed", assetService.removeLogo(campusId));
    }
}
