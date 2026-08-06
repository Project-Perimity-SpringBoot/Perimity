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
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
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

    /**
     * WHO MAY CREATE WHOM. The org chart, as code.
     *
     *   SUPER_ADMIN   the platform. Creates campuses and the one admin who runs
     *                 each; not restricted here.
     *   CAMPUS_ADMIN  staffs their own campus: teaching staff and gate staff.
     *                 NOT students - a campus admin does not know who is in
     *                 which class - and NOT another Campus Admin, because
     *                 appointing your own peer or successor is the Super
     *                 Admin's decision, not yours.
     *   FACULTY       their students, and nobody else.
     *
     * A role absent from this map creates nobody. VISITOR is deliberately not
     * granted to anyone: visitors self-register, and bulk onboarding mints
     * lightweight visitor identities through InternalUserController with the
     * shared key, which does not pass through here.
     *
     * ==========================================================================
     * THIS WAS BRIEFLY WIDENED AND DELIBERATELY PUT BACK
     * ==========================================================================
     * On 2026-08-05 a Campus Admin hitting "a CAMPUS_ADMIN may not create a
     * STUDENT account" looked like a bug, and the map was opened to all roles
     * below SUPER_ADMIN. The team confirmed the original rule is the intended
     * policy, so it is restored.
     *
     * Recording it here because the error message alone does not explain
     * itself, and the next person to meet it will reach the same wrong
     * conclusion. The two constraints are:
     *
     *   students   come from Faculty, who know who is actually in their class,
     *              or from bulk onboarding
     *   admins     are appointed by the Super Admin, because a campus admin
     *              creating a peer is a lateral privilege grant that survives
     *              the original account being deactivated
     *
     * If this needs revisiting, revisit the POLICY with the team - do not widen
     * the map because a screen threw a 403.
     */
    /**
     * Who each role may SEE in the account list.
     *
     * Mirrors CREATABLE deliberately: you see what you may create. A Campus
     * Admin creating a Faculty account they cannot then find would be the
     * obvious way to get this wrong, and keeping the two sets identical means
     * the rule explains itself rather than needing a second one remembered
     * alongside it.
     *
     * A Super Admin is absent and unrestricted - they are platform-wide.
     *
     * This is a real access rule, not a tidier menu. Enforced here rather than
     * by narrowing the dropdown, because ?role=CAMPUS_ADMIN is one curl away
     * from any browser.
     */
    private static final Map<Role, Set<Role>> VISIBLE = Map.of(
            Role.CAMPUS_ADMIN, EnumSet.of(Role.FACULTY, Role.GUARD),
            Role.FACULTY, EnumSet.of(Role.STUDENT));

    private static final Map<Role, Set<Role>> CREATABLE = Map.of(
            Role.CAMPUS_ADMIN, EnumSet.of(Role.FACULTY, Role.GUARD),
            Role.FACULTY, EnumSet.of(Role.STUDENT));

    private final UserAccountService service;
    private final CurrentUser currentUser;

    public UserAdminController(UserAccountService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Create an account")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserCreateDto dto) {
        PerimityPrincipal actor = currentUser.require();

        // A Campus Admin creates accounts on THEIR campus only. Without this a
        // body claiming another campusId would silently create an account there.
        if (!actor.isSuperAdmin()) {
            dto.setCampusId(actor.campusId());
        }

        /*
         * The role annotation above says who may call this endpoint. It cannot
         * say what they may create, and until now nothing did: a Campus Admin
         * could mint a STUDENT or a second CAMPUS_ADMIN, both confirmed live.
         *
         * Checked for everyone except the Super Admin, so "only a Super Admin
         * may create a Super Admin" now falls out of the map rather than being
         * a separate rule that has to be remembered alongside it.
         */
        if (!actor.isSuperAdmin()
                && !CREATABLE.getOrDefault(actor.role(), Set.of()).contains(dto.getRole())) {
            throw new CurrentUser.ForbiddenException(
                    "A " + actor.role() + " may not create a " + dto.getRole() + " account.");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Account created",
                service.create(dto, actor.userId(), actor.role())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit contact details. Email, role and campus cannot change here.")
    public ApiResponse<UserResponse> update(@PathVariable @Positive Long id,
                                            @Valid @RequestBody UserUpdateDto dto) {
        currentUser.requireSelfOrStaff(id);

        // requireSelfOrStaff answers "is this my record, or am I staff" - it says
        // NOTHING about which campus the record belongs to, because it cannot:
        // it only ever sees the target's id. On its own it let a Campus Admin on
        // campus 1 rename the Campus Admin of campus 2, and let a Faculty rename
        // their own Campus Admin. Both were confirmed against the running stack.
        //
        // changeStatus below has always paired the two checks. This is the same
        // pairing, and getOne needs it for the same reason.
        currentUser.requireSameCampus(service.getOne(id).campusId());
        return ApiResponse.ok("Account updated", service.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Activate or deactivate. Nothing is ever hard-deleted.")
    public ApiResponse<UserResponse> changeStatus(@PathVariable @Positive Long id,
                                                  @Valid @RequestBody UserStatusUpdateDto dto) {
        PerimityPrincipal actor = currentUser.require();
        UserResponse target = service.getOne(id);
        currentUser.requireSameCampus(target.campusId());

        /*
         * The same matrix as create, read against the TARGET's role: whoever may
         * bring an account into being is who may take it out of service. Without
         * it, adding FACULTY above would have let a lecturer deactivate their own
         * Campus Admin.
         */
        if (!actor.isSuperAdmin()
                && !CREATABLE.getOrDefault(actor.role(), Set.of()).contains(target.role())) {
            throw new CurrentUser.ForbiddenException(
                    "A " + actor.role() + " may not change the status of a "
                            + target.role() + " account.");
        }

        dto.setChangedBy(actor.userId());
        return ApiResponse.ok("Status changed",
                service.changeStatus(id, dto, actor.userId(), actor.role()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One account. Your own, or any on your campus if you are staff.")
    public ApiResponse<UserResponse> getOne(@PathVariable @Positive Long id) {
        currentUser.requireSelfOrStaff(id);

        // The summary above already promised "any on YOUR CAMPUS". Only the
        // staff half of that was enforced, so staff on any campus could read
        // any account platform-wide - name, email, phone, role and campus.
        UserResponse target = service.getOne(id);
        currentUser.requireSameCampus(target.campusId());
        return ApiResponse.ok(target);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Accounts on your campus, optionally filtered by role")
    public ApiResponse<PageResponse<UserResponse>> list(
            @RequestParam(required = false) Role role,
            @PageableDefault(size = 20) Pageable pageable) {

        PerimityPrincipal actor = currentUser.require();

        // Platform-wide, so unrestricted and campus-agnostic.
        if (actor.isSuperAdmin()) {
            return ApiResponse.ok(service.byCampus(actor.campusId(), role, pageable));
        }

        Set<Role> visible = VISIBLE.getOrDefault(actor.role(), EnumSet.noneOf(Role.class));

        if (role != null && !visible.contains(role)) {
            // Spring's own type, which GlobalExceptionHandler already shapes
            // into a 403 with the standard body. A new exception class would
            // only need wiring up to say the same thing.
            throw new org.springframework.security.access.AccessDeniedException(
                    "Your role cannot view " + role + " accounts.");
        }

        return ApiResponse.ok(service.byCampus(actor.campusId(), role, visible, pageable));
    }

    /** The roles this caller may filter by, so the UI need not guess. */
    @GetMapping("/visible-roles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN','FACULTY')")
    @Operation(summary = "Which roles this caller may list. Drives the filter menu.")
    public ApiResponse<Set<Role>> visibleRoles() {
        PerimityPrincipal actor = currentUser.require();
        return ApiResponse.ok(actor.isSuperAdmin()
                ? EnumSet.allOf(Role.class)
                : VISIBLE.getOrDefault(actor.role(), EnumSet.noneOf(Role.class)));
    }
}
