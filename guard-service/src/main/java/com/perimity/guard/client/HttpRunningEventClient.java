package com.perimity.guard.client;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Behavior 2 - the one call that makes auto-attribution real.
 *
 * A student attending an event holds two valid QRs and will scan whichever is on
 * top. If they scan the DAILY one, this asks gatepass-service whether an event is
 * running for them today, and the entry is credited to it anyway. The organiser's
 * attendance stays accurate and the guard never has to know.
 *
 * gatepass-service already exposes it:
 *   GET /api/gatepass/internal/passes/holder/{id}/running-event
 *
 * ==========================================================================
 * WHY A FAILURE HERE IS SWALLOWED, UNLIKE PASS VERIFICATION
 * ==========================================================================
 * This call cannot deny anyone entry. It only decides which column the entry is
 * counted in. If gatepass-service is slow or down, the right answer is to log a
 * normal campus entry and let the person through - a green light with slightly
 * imperfect attendance is obviously better than a queue at the gate.
 *
 * That is the opposite of HttpPassVerificationClient, where a failure must refuse
 * entry because we genuinely do not know whether the pass is valid. Same kind of
 * outage, opposite correct response, because one call is about access and the
 * other is about bookkeeping.
 *
 * The empty Optional means "no event", which is also what a failure returns.
 * Worth being aware of when reading attendance figures after an incident.
 */
@Component
@ConditionalOnProperty(name = "perimity.guard.clients", havingValue = "http", matchIfMissing = true)
public class HttpRunningEventClient implements RunningEventClient {

    private static final Logger log = LoggerFactory.getLogger(HttpRunningEventClient.class);

    private final RestClient gatepass;

    public HttpRunningEventClient(@Qualifier("gatepassRestClient") RestClient gatepass) {
        this.gatepass = gatepass;
        log.info("HttpRunningEventClient active - Behavior 2 attribution is live.");
    }

    @Override
    public Optional<Long> runningEventFor(Long holderUserId) {
        if (holderUserId == null) {
            return Optional.empty();
        }

        try {
            RunningEventEnvelope response = gatepass.get()
                    .uri("/api/gatepass/internal/passes/holder/{id}/running-event", holderUserId)
                    .retrieve()
                    .body(RunningEventEnvelope.class);

            if (response == null || response.data() == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.data().eventId());

        } catch (RuntimeException ex) {
            // Deliberately not rethrown. See the class comment: attribution must
            // never be the reason a gate stops working.
            log.warn("Behavior 2 lookup failed for holder {} - logging as normal campus entry. {}",
                    holderUserId, ex.getMessage());
            return Optional.empty();
        }
    }

    record RunningEventEnvelope(boolean success, String message, RunningEventView data) { }

    /**
     * Tolerates either shape gatepass might return - a bare id or an object with
     * a name alongside it. Jackson ignores what is absent, so if Tushar adds
     * eventName later the scanner can show "Welcome to [Event]" with no change
     * here beyond reading the field.
     */
    record RunningEventView(Long eventId, String eventName) { }
}
