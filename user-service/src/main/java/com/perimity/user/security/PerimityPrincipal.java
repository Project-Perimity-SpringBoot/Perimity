package com.perimity.user.security;

import io.jsonwebtoken.Claims;

/**
 * Who is making this request. Built from the JWT, never from a request body.
 *
 * This is a copy of the record auth-service publishes. Only the package line
 * differs. The claim names below are the cross-service contract:
 *
 *     sub        user id, as a string
 *     email      login email
 *     name       display name
 *     role       SUPER_ADMIN | CAMPUS_ADMIN | FACULTY | STUDENT | GUARD | VISITOR
 *     campusId   null for SUPER_ADMIN only
 *     iss        "perimity-auth"
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

    /** Only these two verify documents. Faculty may read a profile, not approve an ID proof. */
    public boolean isAdministrative() {
        return role != null && role.isAdministrative();
    }
}
