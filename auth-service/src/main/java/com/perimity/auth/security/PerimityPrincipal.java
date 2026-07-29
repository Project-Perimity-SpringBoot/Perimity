package com.perimity.auth.security;

import com.perimity.auth.entity.enums.Role;
import io.jsonwebtoken.Claims;

/**
 * Who is making this request. Built from the JWT, never from a request body.
 *
 * The five other services each have their own copy of this record. Keep the
 * claim names identical - they are the contract.
 */
public record PerimityPrincipal(Long userId, String email, String name, Role role, Long campusId) {

    public static PerimityPrincipal from(Claims claims) {
        Number campus = claims.get("campusId", Number.class);
        return new PerimityPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("name", String.class),
                Role.valueOf(claims.get("role", String.class)),
                campus == null ? null : campus.longValue());
    }

    public boolean isSuperAdmin() {
        return role == Role.SUPER_ADMIN;
    }

    /** Staff may act on other people's records. A student, guard or visitor may not. */
    public boolean isStaff() {
        return role == Role.SUPER_ADMIN || role == Role.CAMPUS_ADMIN || role == Role.FACULTY;
    }
}
