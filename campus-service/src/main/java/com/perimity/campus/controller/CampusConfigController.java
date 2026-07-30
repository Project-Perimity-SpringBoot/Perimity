package com.perimity.campus.controller;

import com.perimity.campus.dto.ApiResponse;
import com.perimity.campus.dto.request.CampusConfigBulkUpsertDto;
import com.perimity.campus.dto.request.CampusConfigUpsertDto;
import com.perimity.campus.dto.response.CampusConfigResponse;
import com.perimity.campus.service.CampusConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import com.perimity.campus.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Per-campus policy settings.
 *
 * The five other services read from here. Keep the shapes stable - a rename
 * breaks callers you cannot see from this repository folder.
 */
@RestController
@RequestMapping("/api/campus/campuses/{campusId}/config")
@Validated
@Tag(name = "Campus config", description = "Per-campus policy, as typed key-value settings")
public class CampusConfigController {

    private final CampusConfigService service;

    public CampusConfigController(CampusConfigService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Every setting for this campus")
    public ApiResponse<List<CampusConfigResponse>> list(@PathVariable @Positive Long campusId) {
        return ApiResponse.ok(service.list(campusId));
    }

    @GetMapping("/{key}")
    @Operation(summary = "One setting. This is the call other services make.")
    public ApiResponse<CampusConfigResponse> get(
            @PathVariable @Positive Long campusId,
            @PathVariable @NotBlank String key) {

        return ApiResponse.ok(service.get(campusId, key));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Create or replace one setting")
    public ApiResponse<CampusConfigResponse> upsert(
            @PathVariable @Positive Long campusId,
            @PathVariable @NotBlank String key,
            @Valid @RequestBody CampusConfigUpsertDto dto) {

        // The path is the authority. The body must agree rather than being
        // silently overwritten - a mismatch means the caller thinks they are
        // writing a different setting than they are, and that should be loud.
        //
        // Not @JsonIgnore'd like other path-derived fields, because the bulk
        // endpoint below legitimately needs configKey in the body.
        if (!key.equals(dto.getConfigKey())) {
            throw new IllegalArgumentException(
                    "The setting key in the path (" + key
                            + ") does not match the body (" + dto.getConfigKey() + ").");
        }
        return ApiResponse.ok("Setting saved", service.upsert(campusId, dto));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Save the whole settings screen at once. All or nothing.")
    public ApiResponse<List<CampusConfigResponse>> upsertAll(
            @PathVariable @Positive Long campusId,
            @Valid @RequestBody CampusConfigBulkUpsertDto dto) {

        return ApiResponse.ok("Settings saved", service.upsertAll(campusId, dto));
    }

    @PostMapping("/restore-defaults")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Add back any missing default. Existing settings are untouched.")
    public ApiResponse<List<CampusConfigResponse>> restoreDefaults(
            @PathVariable @Positive Long campusId) {

        List<CampusConfigResponse> added = service.restoreMissingDefaults(campusId);
        return ApiResponse.ok(added.size() + " missing default(s) restored", added);
    }
}
