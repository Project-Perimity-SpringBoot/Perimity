package com.perimity.campus.controller;

import com.perimity.campus.dto.ApiResponse;
import com.perimity.campus.dto.response.CampusConfigResponse;
import com.perimity.campus.dto.response.CampusGateResponse;
import com.perimity.campus.dto.response.CampusResponse;
import com.perimity.campus.service.CampusConfigService;
import com.perimity.campus.service.CampusGateService;
import com.perimity.campus.service.CampusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Service-to-service reads. Guarded by X-Internal-Api-Key, never public.
 *
 * These exist because the other five services hold a campusId and need the
 * campus NAME for a PDF, or the CODE for a storage prefix, or a policy value.
 * They cannot read campusdb directly - database-per-service is the one rule the
 * architecture does not bend.
 *
 * Read-only on purpose. No service should be able to change another's data
 * through a shared key; a compromised key would then be a compromised platform.
 *
 * CALLERS TODAY:
 *   gatepass-service  campus name + code, to build the QR job
 *   guard-service     the gate list, for the shift picker, and the single-gate
 *                     check it makes when a shift actually starts
 *   any service       a config value
 */
@RestController
@RequestMapping("/api/campus/internal")
@Validated
@Tag(name = "Internal", description = "Service-to-service reads. Not for browsers.")
public class CampusInternalController {

    private final CampusService campusService;
    private final CampusGateService gateService;
    private final CampusConfigService configService;

    public CampusInternalController(CampusService campusService,
                                    CampusGateService gateService,
                                    CampusConfigService configService) {
        this.campusService = campusService;
        this.gateService = gateService;
        this.configService = configService;
    }

    @GetMapping("/campuses/{id}")
    @Operation(summary = "Campus name and code. gatepass-service calls this for every QR job.")
    public ApiResponse<CampusResponse> campus(@PathVariable @Positive Long id) {
        return ApiResponse.ok(campusService.getOne(id));
    }

    @GetMapping("/campuses/by-code/{code}")
    @Operation(summary = "Resolve a code to a campus")
    public ApiResponse<CampusResponse> byCode(@PathVariable @NotBlank String code) {
        return ApiResponse.ok(campusService.getByCode(code));
    }

    @GetMapping("/campuses/{id}/gates")
    @Operation(summary = "Active gates. guard-service fills its shift picker from this.")
    public ApiResponse<List<CampusGateResponse>> gates(@PathVariable @Positive Long id) {
        return ApiResponse.ok(gateService.listActive(id));
    }


    /**
     * Day 11. One gate, proven usable for a shift starting now.
     *
     * The list above fills the picker; this validates what comes back from it.
     * They are not the same question and the second is the one that matters:
     * a picker is a suggestion, and guard-service currently stores the campusId,
     * gateId and gateName it is handed without checking any of them - after
     * which the gate name is copied into every entry log for that shift.
     *
     * 404 when the gate belongs to another campus. 400 when it exists here but
     * has been decommissioned - a different problem with a different fix, and
     * the guard needs to be told which.
     */
    @GetMapping("/campuses/{campusId}/gates/{gateId}")
    @Operation(summary = "Validate one gate for a shift start. 404 if it is not this "
            + "campus's, 400 if it is out of service.")
    public ApiResponse<CampusGateResponse> gateForShift(@PathVariable @Positive Long campusId,
                                                        @PathVariable @Positive Long gateId) {
        return ApiResponse.ok(gateService.requireActiveForShift(campusId, gateId));
    }

    @GetMapping("/campuses/{id}/config/{key}")
    @Operation(summary = "One policy value")
    public ApiResponse<CampusConfigResponse> config(@PathVariable @Positive Long id,
                                                    @PathVariable @NotBlank String key) {
        return ApiResponse.ok(configService.get(id, key));
    }

    @GetMapping("/campuses/{id}/config")
    @Operation(summary = "Every policy value for this campus")
    public ApiResponse<List<CampusConfigResponse>> allConfig(@PathVariable @Positive Long id) {
        return ApiResponse.ok(configService.list(id));
    }
}
