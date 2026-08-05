package com.perimity.auth.messaging.contract;

/**
 * Published when an account is created. user-service consumes it and provisions
 * the matching profile.
 *
 * ==========================================================================
 * THE CONTRACT. CHANGE IT CAREFULLY.
 * ==========================================================================
 * user-service has its own copy of this record - a different package, the same
 * field names. JSON on the wire means Jackson matches by NAME, so:
 *
 *   ADDING a field is safe. An older consumer ignores what it does not know.
 *   RENAMING or REMOVING one is not. The consumer silently reads null and
 *   provisions a profile with no campus, which fails a NOT NULL constraint at
 *   3am rather than at compile time.
 *
 * If a field here changes, change user-service's copy in the same commit.
 *
 * ==========================================================================
 * WHAT IS DELIBERATELY ABSENT
 * ==========================================================================
 * No name, no phone, no password hash. The profile does not store any of them -
 * auth-service's User.name stays the authoritative name - and putting a
 * credential-adjacent field on a queue that persists messages to disk is how
 * personal data ends up somewhere nobody remembers to clean up.
 *
 * email is carried only so log lines about a failed provisioning name a human
 * rather than an integer. Nothing writes it to a profile.
 *
 * @param userId    the account this profile will belong to
 * @param email     for logs and the DLQ, never stored on the profile
 * @param role      decides which kind of profile, or none at all
 * @param campusId  null only for SUPER_ADMIN, which gets no profile anyway
 */
public record UserCreatedEvent(
        Long userId,
        String email,
        String role,
        Long campusId
) {
}
