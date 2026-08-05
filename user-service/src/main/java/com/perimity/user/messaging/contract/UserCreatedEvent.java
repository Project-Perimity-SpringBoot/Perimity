package com.perimity.user.messaging.contract;

/**
 * The consumer's copy of auth-service's UserCreatedEvent.
 *
 * ==========================================================================
 * A COPY, NOT A SHARED MODULE, AND THAT IS DELIBERATE
 * ==========================================================================
 * Sharing a jar between services would couple their release cycles: changing
 * one field would mean rebuilding and redeploying both together, which is the
 * thing microservices exist to avoid. Every other cross-service contract in
 * this repo is duplicated the same way - PerimityPrincipal, ValidationPatterns
 * and QrGenerationJob are all copies.
 *
 * The cost is that the two can drift, and Jackson matches by NAME, so drift is
 * silent: a renamed field arrives as null and provisions a profile with no
 * campus. Keep the field names identical, and change both in one commit.
 *
 * role is a String, not the Role enum. An enum here would throw on any value
 * this service does not know, which means adding a role to auth-service would
 * start dead-lettering every message until user-service was redeployed too.
 * A String degrades to "a role I have no profile for", which is the correct
 * behaviour for a consumer that only cares about two of them.
 */
public record UserCreatedEvent(
        Long userId,
        String email,
        String role,
        Long campusId
) {
}
