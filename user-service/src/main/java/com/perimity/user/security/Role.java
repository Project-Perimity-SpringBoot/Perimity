package com.perimity.user.security;

/**
 * The six user classes, mirrored from auth-service.
 *
 * WHY A COPY AND NOT A SHARED JAR
 * user-service never persists a Role and never decides one. It only reads the
 * "role" claim out of a JWT that auth-service issued. A shared module would
 * couple six services' build order together to share one enum with six values.
 *
 * The names below are a CONTRACT with auth-service's Role enum. If a value is
 * ever renamed there, every service that reads the claim - this one included -
 * stops recognising it and treats the caller as unauthenticated. Announce a
 * change before making it.
 */
public enum Role {

    SUPER_ADMIN,
    CAMPUS_ADMIN,
    FACULTY,
    STUDENT,
    VISITOR,
    GUARD;

    /** Super Admin is platform-wide, so campusId is NULL only for them. */
    public boolean requiresCampus() {
        return this != SUPER_ADMIN;
    }

    public boolean isAdministrative() {
        return this == SUPER_ADMIN || this == CAMPUS_ADMIN;
    }
}
