package com.perimity.campus.security;

import io.jsonwebtoken.Claims;

/**
 * Who is making this request, read out of the JWT.
 *
 * campusId comes from the token, never from a query parameter. That single
 * change is the point of Day 7: before it, any caller could pass
 * ?campusId=2 and read another institution's data. Multi-tenancy that depends
 * on the client telling the truth is not multi-tenancy.
 *
 * SUPER_ADMIN is the one role with a null campusId - they are platform-wide.
 */
public record PerimityPrincipal(
        Long userId,
        String email,
        String name,
        String role,
        Long campusId
) {

    /**
     * The claim names auth-service writes. If Omkar changes any of these, this
     * is the only file in campus-service that needs to change - which is why
     * they are constants rather than string literals scattered through a filter.
     */
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_CAMPUS = "campusId";

    public static PerimityPrincipal from(Claims claims) {
        Number campus = claims.get(CLAIM_CAMPUS, Number.class);
        return new PerimityPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                claims.get(CLAIM_ROLE, String.class),
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
