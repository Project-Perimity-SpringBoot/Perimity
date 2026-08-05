package com.perimity.user.entity.enums;

/**
 * What happened to one row of an import.
 *
 * Recorded per row because a batch of two hundred with three bad rows is a
 * success with three things to fix, not a failure. Faculty need to know which
 * three and why, in terms they can act on - "row 47: roll number CS-101 is
 * already used on this campus" rather than a stack trace or a count.
 */
public enum ImportRowOutcome {

    /** Validated but not yet written. The state every row starts in. */
    PENDING,

    /** Account and profile created, verified against the uploading faculty. */
    CREATED,

    /**
     * The email already had an account, so the existing one was updated
     * instead of a second being made.
     *
     * The common case, not an edge case: students resubmit forms, and a second
     * account on the same address would split one person's history in two and
     * could issue them a second pass. Matching on email is what stops that.
     */
    UPDATED,

    /**
     * The row was rejected and nothing was written for it. `message` says why,
     * in words faculty can act on.
     */
    REJECTED;

    public boolean wroteSomething() {
        return this == CREATED || this == UPDATED;
    }
}
