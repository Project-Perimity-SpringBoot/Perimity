package com.perimity.guard.client;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls user-service for the holder's photo key.
 *
 *   GET {user}/api/user/internal/profiles/{userId}/summary
 *
 * ==========================================================================
 * WHAT IS STILL MISSING, AND IT IS NOT THIS CLASS
 * ==========================================================================
 * The endpoint returns `photoS3Key` - an object-storage key. A browser cannot
 * render a key. user-service has presigned-URL endpoints, but only on the public
 * profile controllers, which authenticate a USER's JWT; guard-service calls
 * server-to-server with the shared internal key and has no holder token to
 * present, nor any business holding one.
 *
 * So FR-SCAN-9 is not finished until user-service exposes something like
 *
 *   GET /api/user/internal/profiles/{userId}/photo-url
 *
 * returning a short-lived presigned URL. Until then this returns the key, the
 * scanner shows a placeholder, and the ask to Mukul is one endpoint rather than
 * a vague "we need photos".
 *
 * ==========================================================================
 * WHY A FAILURE HERE IS SWALLOWED
 * ==========================================================================
 * Same reasoning as Behavior 2, opposite of pass verification: this call cannot
 * deny anyone entry. It decorates a decision that has already been made. If
 * user-service is slow or down the right answer is a green card with a name and
 * no photo - not a queue at the gate, and certainly not a refusal.
 */
@Component
@ConditionalOnProperty(name = "perimity.guard.clients.profile", havingValue = "http", matchIfMissing = true)
public class HttpHolderProfileClient implements HolderProfileClient {

    private static final Logger log = LoggerFactory.getLogger(HttpHolderProfileClient.class);

    private final RestClient user;

    public HttpHolderProfileClient(@Qualifier("userRestClient") RestClient user) {
        this.user = user;
    }

    @Override
    public Optional<HolderProfile> profileFor(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            SummaryEnvelope response = user.get()
                    .uri("/api/user/internal/profiles/{id}/summary", userId)
                    .retrieve()
                    .body(SummaryEnvelope.class);

            if (response == null || response.data() == null) {
                return Optional.empty();
            }
            SummaryView v = response.data();
            return Optional.of(new HolderProfile(v.userId(), v.identifierCode(), v.photoS3Key()));

        } catch (RuntimeException ex) {
            // debug, not warn: a visitor has no profile in user-service at all,
            // so 404 is the normal case for a large share of scans. Warning on it
            // would bury the failures that matter.
            log.debug("No profile for holder {} ({})", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    record SummaryEnvelope(boolean success, String message, SummaryView data) { }

    /** Only the three fields the gate is entitled to. Jackson ignores the rest. */
    record SummaryView(Long userId, String identifierCode, String photoS3Key) { }
}
