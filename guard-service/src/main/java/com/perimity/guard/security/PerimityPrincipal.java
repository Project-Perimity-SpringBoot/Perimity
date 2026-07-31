package com.perimity.guard.security;

import io.jsonwebtoken.Claims;

/**
 * Who is making this request. Built from the JWT, never from a request body.
 *
 * THE CLAIM CONTRACT - identical in all six services:
 *
 *     sub        user id, as a string
 *     email      login email
 *     name       display name
 *     role       SUPER_ADMIN | CAMPUS_ADMIN | FACULTY | STUDENT | GUARD | VISITOR
 *     campusId   null for SUPER_ADMIN only
 *     iss        "perimity-auth"
 *
 * Renaming any of these breaks every service silently - they see a null and
 * treat the caller as unauthenticated. Announce a change before making it.
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

    public boolean isStaff() {
        return role.isStaff();
    }
}
