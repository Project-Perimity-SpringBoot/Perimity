package com.perimity.guard.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Circuit breakers for the two hops on the scan path.
 *
 * ==========================================================================
 * WHAT THIS CHANGES, AND WHAT IT DELIBERATELY DOES NOT
 * ==========================================================================
 * It does NOT change who gets in. A scan that could not be verified is still
 * refused - HttpPassVerificationClient still throws
 * PassVerificationUnavailableException and the guard still sees the outage
 * card. Fail-closed is the whole point and a breaker must not soften it.
 *
 * What changes is how long being refused takes. Without a breaker, every scan
 * against a dead qr-service waits the full 400ms connect timeout, per hop, per
 * scan, forever. Twenty people in a queue is twenty sequential timeouts and a
 * request thread held for each. With the breaker open the same refusal is
 * immediate, and the guard learns the scanner is down in one scan instead of
 * inferring it from twenty slow ones.
 *
 * Same answer, honest latency.
 *
 * ==========================================================================
 * WHY 4xx IS IGNORED - THIS IS THE IMPORTANT LINE
 * ==========================================================================
 * ignoreExceptions(HttpClientErrorException) keeps client errors out of the
 * failure count. Without it, a run of invalid or expired tokens - which is
 * NORMAL traffic at a gate, and is exactly what the system exists to reject -
 * would trip the breaker and take verification down for everyone.
 *
 * That failure mode is worth naming plainly: a breaker that counts 4xx turns
 * "somebody presented a bad QR" into a self-inflicted outage, and hands anyone
 * with a screenshot of a revoked pass a way to close the gate. Only transport
 * failures and 5xx - the peer being genuinely unwell - should count.
 *
 * ==========================================================================
 * SEPARATE BREAKERS PER HOP
 * ==========================================================================
 * qr-service and gatepass-service fail independently, so they get independent
 * breakers. A shared one would let a sick qr-service suppress calls to a
 * perfectly healthy gatepass-service, and would make the health endpoint lie
 * about which peer is actually at fault.
 */
@Configuration
public class ScanBreakerConfig {

    private static final Logger log = LoggerFactory.getLogger(ScanBreakerConfig.class);

    /**
     * Deliberately larger than the failure threshold suggests. A gate is
     * low-volume - a handful of scans a minute at most - so a window of 20
     * covers several minutes of real traffic and stops one bad thirty-second
     * patch from latching the breaker open.
     */
    @Value("${perimity.guard.breaker.window-size:20}")
    private int windowSize;

    /**
     * Nothing opens before this many calls have been recorded. Without it the
     * first failed scan after a restart is a 100% failure rate and the breaker
     * opens on a sample of one - which is how a service that is merely still
     * warming up gets declared dead.
     */
    @Value("${perimity.guard.breaker.minimum-calls:5}")
    private int minimumCalls;

    @Value("${perimity.guard.breaker.failure-rate-percent:50}")
    private float failureRatePercent;

    /**
     * How long to stay open before probing again. Short on purpose: a gate that
     * stays shut for a minute after the peer recovered is its own incident.
     */
    @Value("${perimity.guard.breaker.open-seconds:10}")
    private long openSeconds;

    @Bean
    CircuitBreaker qrDecryptBreaker() {
        return named("qr-decrypt", "qr-service token decrypt");
    }

    @Bean
    CircuitBreaker gatepassPassBreaker() {
        return named("gatepass-pass", "gatepass-service pass lookup");
    }

    private CircuitBreaker named(String name, String what) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRatePercent)
                .waitDurationInOpenState(Duration.ofSeconds(openSeconds))
                .permittedNumberOfCallsInHalfOpenState(3)
                // Recover without needing traffic. A gate can be quiet for an
                // hour; the breaker should not need somebody to walk up and be
                // refused before it is willing to notice the peer came back.
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // See the class comment. This is the line that stops normal
                // rejections from becoming an outage.
                .ignoreExceptions(HttpClientErrorException.class)
                .build();

        CircuitBreaker breaker = CircuitBreaker.of(name, config);

        // Transitions are the only thing worth logging here - per-call logging
        // on the scan path would be noise at exactly the moment the log needs
        // to be readable.
        //
        // The effect is spelled out per state rather than as "open or not".
        // HALF_OPEN is the state most likely to be misread: it permits a few
        // calls through specifically to test recovery, so describing it as
        // "refusing" tells whoever is reading the log the opposite of what is
        // happening while they are watching a peer come back.
        breaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.State to = event.getStateTransition().getToState();
            String effect = switch (to) {
                case CLOSED -> "verifying normally again";
                case OPEN -> "being refused without calling the peer";
                case HALF_OPEN -> "being trialled - a few calls reach the peer to test recovery";
                case DISABLED -> "always calling the peer - the breaker is off";
                case FORCED_OPEN -> "always refused - the breaker is forced open";
                case METRICS_ONLY -> "calling the peer - the breaker is recording only";
            };
            log.warn("Circuit breaker '{}' ({}): {} -> {}. Scans are {}.",
                    name, what, event.getStateTransition().getFromState(), to, effect);
        });

        return breaker;
    }
}
