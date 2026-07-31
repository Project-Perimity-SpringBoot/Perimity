package com.perimity.guard.config;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Reports whether each service guard-service depends on is actually reachable.
 *
 * SRS 5.6: "each service shall expose a health endpoint reporting its own status
 * and the reachability of its database and message broker."
 *
 * ==========================================================================
 * WHY THIS EXISTS, IN ONE SENTENCE
 * ==========================================================================
 * Without it, a failed scan is indistinguishable from a wrong internal key,
 * a wrong service URL, and a service that is simply not running - all three
 * arrive as the same 503, and the only way to tell them apart is to read logs on
 * someone else's machine.
 *
 * On an integration day that difference is most of the afternoon.
 *
 * ==========================================================================
 * WHAT "DOWN" MEANS HERE
 * ==========================================================================
 * Only that guard-service cannot reach it. It is not a claim about whether that
 * service is healthy in its own right - each owner has their own health endpoint
 * for that. This answers exactly one question, which is the one you need when a
 * scan just failed: can I talk to it from here, with the credentials I have.
 *
 * Each check hits the target's public /ping, which needs no key and no token.
 * That is deliberate: a 401 from an internal endpoint would mean "reachable but
 * misconfigured", and conflating that with "unreachable" would hide the single
 * most common integration fault on this project - a mismatched INTERNAL_API_KEY.
 * Reachability first; the key shows up the moment you try a real scan.
 */
@Configuration
public class DownstreamHealthConfig {

    /** Long enough to distinguish "slow" from "gone", short enough not to hang a dashboard. */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    @Bean
    HealthIndicator qrServiceHealth(@Qualifier("qrRestClient") RestClient qr) {
        return probe(qr, "/api/qr/ping", "qr-service", "token decrypt");
    }

    @Bean
    HealthIndicator gatepassServiceHealth(@Qualifier("gatepassRestClient") RestClient gatepass) {
        return probe(gatepass, "/api/gatepass/ping", "gatepass-service",
                "pass status and running-event lookup");
    }

    @Bean
    HealthIndicator campusServiceHealth(@Qualifier("campusRestClient") RestClient campus) {
        return probe(campus, "/api/campus/ping", "campus-service", "repeat_entry_result policy");
    }

    @Bean
    HealthIndicator userServiceHealth(@Qualifier("userRestClient") RestClient user) {
        return probe(user, "/api/user/ping", "user-service", "holder photo");
    }

    /**
     * A probe reports DOWN but the SERVICE stays UP, on purpose.
     *
     * Only qr-service and gatepass-service can stop a scan. campus-service and
     * user-service failing degrade a scan - default policy, no photo - and the
     * gate keeps working. Marking guard-service itself DOWN because a photo
     * lookup is unavailable would take a working gate out of a load balancer,
     * which is a far worse outcome than a green card with no face on it.
     *
     * So this endpoint is diagnostic, not a liveness signal. Read it, do not
     * automate a restart from it.
     */
    private HealthIndicator probe(RestClient client, String path, String service, String usedFor) {
        return () -> {
            Instant started = Instant.now();
            try {
                client.get().uri(path).retrieve().toBodilessEntity();
                return Health.up()
                        .withDetail("service", service)
                        .withDetail("usedFor", usedFor)
                        .withDetail("responseMs", Duration.between(started, Instant.now()).toMillis())
                        .build();
            } catch (RuntimeException ex) {
                return Health.down()
                        .withDetail("service", service)
                        .withDetail("usedFor", usedFor)
                        .withDetail("reason", ex.getClass().getSimpleName() + ": " + ex.getMessage())
                        .build();
            }
        };
    }
}
