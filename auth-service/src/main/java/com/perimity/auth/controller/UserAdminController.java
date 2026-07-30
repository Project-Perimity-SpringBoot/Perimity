package com.perimity.auth.controller;

import com.perimity.auth.dto.ApiResponse;
import com.perimity.auth.dto.request.UserCreateDto;
import com.perimity.auth.dto.request.UserStatusUpdateDto;
import com.perimity.auth.dto.request.UserUpdateDto;
import com.perimity.auth.dto.response.PageResponse;
import com.perimity.auth.dto.response.UserResponse;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.security.CurrentUser;
import com.perimity.auth.security.PerimityPrincipal;
import com.perimity.auth.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Account administration.
 *
 * campusId comes from the token, never from a parameter. Before Day 7 a Campus
 * Admin could have listed another institution's accounts by editing a query
 * string - multi-tenancy that trusts the client is not multi-tenancy.
 */
@RestController
@RequestMapping("/api/auth/users")
@Validated
@Tag(name = "Accounts", description = "Create and manage accounts")
public class UserAdminController {

    private final UserAccountService service;
    private final CurrentUser currentUser;

    public UserAdminController(UserAccountService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Create an account")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserCreateDto dto) {
        PerimityPrincipal actor = currentUser.require();

        // A Campus Admin creates accounts on THEIR campus only. Without this a
        // body claiming another campusId would silently create an account there.
        if (!actor.isSuperAdmin()) {
            dto.setCampusId(actor.campusId());
        }
        if (dto.getRole() == Role.SUPER_ADMIN && !actor.isSuperAdmin()) {
            throw new CurrentUser.ForbiddenException("Only a Super Admin may create a Super Admin.");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Account created",
                service.create(dto, actor.userId(), actor.role())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit contact details. Email, role and campus cannot change here.")
    public ApiResponse<UserResponse> update(@PathVariable @Positive Long id,
                                            @Valid @RequestBody UserUpdateDto dto) {
        currentUser.requireSelfOrStaff(id);
        return ApiResponse.ok("Account updated", service.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')")
    @Operation(summary = "Activate or deactivate. Nothing is ever hard-deleted.")
    public ApiResponse<UserResponse> changeStatus(@PathVariable @Positive Long id,
                                                  @Valid @RequestBody UserStatusUpdateDto dto) {
        PerimityPrincipal actor = currentUser.require();
        UserResponse target = service.getOne(id);
        currentUser.requireSameCampus(target.campusId());

        dto.setChangedBy(actor.userId());
        return ApiResponse.ok("Status changed",
                service.changeStatus(id, dto, actor.userId(), actor.role()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One account. Your own, or any on your campus if you are staff.")
    public ApiResponse<UserResponse> getOne(@PathVariable @Positive Long id) {
        currentUser.requireSelfOrStaff(id);
        return ApiResponse.ok(service.getOne(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Accounts on your campus, optionally filtered by role")
    public ApiResponse<PageResponse<UserResponse>> list(
            @RequestParam(required = false) Role role,
            @PageableDefault(size = 20) Pageable pageable) {

        return ApiResponse.ok(service.byCampus(currentUser.campusId(), role, pageable));
    }
}
