package com.perimity.auth.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * PROPOSAL. auth-service's first outbound call to another service.
 *
 * Until now this service had no HTTP client of any kind, which is the real
 * reason gatepass-service's "auth-service confirms the email OTP" endpoint has
 * never had a caller: there was nothing here that could call it. Because
 * VisitorRequestService.markEmailVerified is what issues the pass, that gap
 * means no visitor has ever been issued one.
 *
 * ======================================================================
 *  WHY THIS IS BEST-EFFORT AND NEVER FAILS THE SIGN-IN
 * ======================================================================
 * The visitor has already proved they own the address by the time this runs.
 * Their token is minted either way. If gatepass-service is down, the correct
 * outcome is a signed-in visitor whose pass is late - not a visitor who cannot
 * sign in because a different service is unhealthy.
 *
 * That makes the failure silent, so it is logged at ERROR with the email and
 * the reason. A pass that never arrives is otherwise indistinguishable from a
 * visitor who mistyped their address, and this log line is the difference.
 *
 * The retry story is deliberately absent: a redelivery mechanism belongs on the
 * queue that already exists between these services, not in a hand-rolled loop
 * here. Noted in the PR rather than half-built.
 */
@Component
public class GatepassVisitorClient {

    private static final Logger log = LoggerFactory.getLogger(GatepassVisitorClient.class);

    /** Matches InternalApiKeyFilter.HEADER in every service that has one. */
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient http;

    public GatepassVisitorClient(RestClient.Builder builder,
                                 @Value("${perimity.gatepass.base-url}") String baseUrl,
                                 @Value("${perimity.internal.api-key}") String apiKey) {
        this.http = builder
                .baseUrl(baseUrl)
                .defaultHeader(INTERNAL_KEY_HEADER, apiKey)
                .build();
    }

    /**
     * Tells gatepass-service that this visitor's email is confirmed.
     *
     * Addressed by email because that is what an OTP verification actually
     * knows. See VisitorRequestService.markEmailVerifiedByEmail for why the
     * holder identity travels in the body instead.
     *
     * @return true if gatepass accepted it, false if the call failed. Callers
     *         use this for logging only - a false must not change what the
     *         visitor sees.
     */
    public boolean markEmailVerified(String visitorEmail, Long visitorUserId) {
        try {
            http.post()
                    .uri("/api/gatepass/internal/visitor-requests/by-email/{email}/verified",
                            visitorEmail)
                    .body(new VisitorEmailVerified(visitorUserId))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Visitor {} email verified in gatepass-service", visitorUserId);
            return true;

        } catch (RestClientException ex) {
            // 404 lands here too, and it is not always a fault: a visitor can
            // verify a LOGIN code without any request pending. Same log either
            // way - this service cannot tell the two apart, and gatepass has
            // the context to say which it was.
            log.error("Could not mark visitor {} ({}) verified in gatepass-service. "
                            + "Their pass will not be issued until this is retried: {}",
                    visitorUserId, visitorEmail, ex.getMessage());
            return false;
        }
    }

    /** Mirrors gatepass-service's VisitorEmailVerifiedDto. */
    public record VisitorEmailVerified(Long visitorUserId) {
    }
}
