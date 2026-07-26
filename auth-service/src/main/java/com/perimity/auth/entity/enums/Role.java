package com.perimity.auth.entity.enums;

/**
 * The six user classes.
 *
 * IMPORTANT - login is NOT the same for every role. The v1.0 assumption of
 * "passwordless everywhere" was wrong and contradicted the SRS. This enum is
 * the single place that truth lives, so no controller has to guess.
 *
 *   Super Admin  - password only
 *   Campus Admin - password only
 *   Guard        - password only
 *   Faculty      - password OR OTP, user's choice
 *   Student      - password OR OTP, user's choice
 *   Visitor      - OTP only, never has a password
 */
public enum Role {

    SUPER_ADMIN,
    CAMPUS_ADMIN,
    FACULTY,
    STUDENT,
    VISITOR,
    GUARD;

    /** A visitor never has a password_hash. Everyone else must. */
    public boolean canLoginWithPassword() {
        return this != VISITOR;
    }

    /** Faculty and Student may choose OTP. A visitor has no other option. */
    public boolean canLoginWithOtp() {
        return this == FACULTY || this == STUDENT || this == VISITOR;
    }

    /** Super Admin is platform-wide, so campus_id is NULL only for them. */
    public boolean requiresCampus() {
        return this != SUPER_ADMIN;
    }

    public boolean isAdministrative() {
        return this == SUPER_ADMIN || this == CAMPUS_ADMIN;
    }
}
