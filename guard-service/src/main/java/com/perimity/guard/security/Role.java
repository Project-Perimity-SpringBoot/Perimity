package com.perimity.guard.security;

/**
 * The six user classes, as they appear in the JWT "role" claim.
 *
 * A local copy on purpose. This is a cross-service contract, not a persisted
 * entity of this service, and no service reads another service's database - so
 * it cannot import auth-service's enum. If a value is ever added, it is added
 * in all six or the token stops parsing in the ones that missed it.
 *
 * Order and spelling must match auth-service exactly.
 */
public enum Role {

    SUPER_ADMIN,
    CAMPUS_ADMIN,
    FACULTY,
    STUDENT,
    VISITOR,
    GUARD;

    /** Staff may act on other people's records. A student, guard or visitor may not. */
    public boolean isStaff() {
        return this == SUPER_ADMIN || this == CAMPUS_ADMIN || this == FACULTY;
    }

    public boolean isAdministrative() {
        return this == SUPER_ADMIN || this == CAMPUS_ADMIN;
    }
}
