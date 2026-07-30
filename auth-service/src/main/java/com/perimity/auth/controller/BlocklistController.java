package com.perimity.auth.controller;

import com.perimity.auth.dto.ApiResponse;
import com.perimity.auth.dto.request.BlocklistCreateDto;
import com.perimity.auth.dto.response.BlocklistEntryResponse;
import com.perimity.auth.dto.response.PageResponse;
import com.perimity.auth.security.CurrentUser;
import com.perimity.auth.security.PerimityPrincipal;
import com.perimity.auth.service.BlocklistService;
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
 * The per-campus blocklist.
 *
 * ADMIN ONLY, all of it. This must never be reachable by the person it
 * describes - per FR-BLK-4 a blocked registration is refused with a
 * deliberately vague message, and that promise is worthless if the same person
 * can read the list.
 */
@RestController
@RequestMapping("/api/auth/blocklist")
@Validated
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
@Tag(name = "Blocklist", description = "Bar a person from one campus. Admin only.")
public class BlocklistController {

    private final BlocklistService service;
    private final CurrentUser currentUser;

    public BlocklistController(BlocklistService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "Block an email or phone at your campus")
    public ResponseEntity<ApiResponse<BlocklistEntryResponse>> add(
            @Valid @RequestBody BlocklistCreateDto dto) {

        PerimityPrincipal actor = currentUser.require();
        if (!actor.isSuperAdmin()) {
            dto.setCampusId(actor.campusId());
        }
        dto.setCreatedBy(actor.userId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Entry added",
                service.add(dto, actor.userId(), actor.role())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove an entry. A hard delete - the audit row is the record.")
    public ApiResponse<Void> remove(@PathVariable @Positive Long id) {
        PerimityPrincipal actor = currentUser.require();
        service.remove(currentUser.campusId(), id, actor.userId(), actor.role());
        return ApiResponse.ok("Entry removed", null);
    }

    @GetMapping
    @Operation(summary = "Entries at your campus")
    public ApiResponse<PageResponse<BlocklistEntryResponse>> list(
            @RequestParam(required = false) String email,
            @PageableDefault(size = 20) Pageable pageable) {

        return ApiResponse.ok(service.list(currentUser.campusId(), email, pageable));
    }

    @GetMapping("/count")
    @Operation(summary = "How many entries, for the dashboard")
    public ApiResponse<Map<String, Long>> count() {
        return ApiResponse.ok(Map.of("blocked", service.count(currentUser.campusId())));
    }
}
