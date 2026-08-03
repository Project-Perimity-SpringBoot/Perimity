package com.perimity.qr.security;

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
 * role is kept as a String here rather than an enum, the same way
 * gatepass-service does it. qr-service makes no role decision in Java - every
 * rule it has is a matcher in SecurityConfig - so an enum would be a second
 * copy of a list that already lives in auth-service, and a new role added
 * there would fail to parse here rather than simply not matching.
 */
public record PerimityPrincipal(
        Long userId,
        String email,
        String name,
        String role,
        Long campusId
) {

    public static PerimityPrincipal from(Claims claims) {
        Number campus = claims.get("campusId", Number.class);
        return new PerimityPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("name", String.class),
                claims.get("role", String.class),
                campus == null ? null : campus.longValue());
    }

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(role);
    }

    /** Staff can act on other people's records. A student or visitor cannot. */
    public boolean isStaff() {
        return "SUPER_ADMIN".equals(role)
                || "CAMPUS_ADMIN".equals(role)
                || "FACULTY".equals(role);
    }
}
