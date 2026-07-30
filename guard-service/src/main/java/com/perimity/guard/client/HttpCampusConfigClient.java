package com.perimity.guard.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads `repeat_entry_result` from campus-service.
 *
 * ==========================================================================
 * WHY THIS IS CACHED
 * ==========================================================================
 * A scan already makes two network calls and has under a second to answer. A
 * third hop on every scan, for a value that changes perhaps twice a year, would
 * be the slowest possible way to learn something almost always identical to last
 * time. So it is cached per campus for a few minutes.
 *
 * The staleness that buys is acceptable and worth stating plainly: change the
 * key and gates take up to the TTL to notice. Nobody is admitted or refused
 * differently in the meantime - only the colour of an already-permitted entry
 * changes - so the cost of being briefly wrong is close to zero.
 *
 * ==========================================================================
 * WHY IT FAILS SOFT
 * ==========================================================================
 * FR-CFG-3 says an unset key falls back to a documented default, and AMBER is
 * that default. A campus-service that is down is indistinguishable, from here,
 * from a campus that has not configured the key - and in both cases the right
 * answer is the default rather than an error. Refusing to scan because a display
 * preference is unreadable would be absurd.
 *
 * This is also what lets the branch work TODAY: campus-service currently ships
 * `repeat.entry.allowed`, a boolean, not `repeat_entry_result`. Every lookup
 * 404s and every scan quietly uses AMBER - which is exactly the specified
 * behaviour, not a workaround. When the key is renamed this starts reading it
 * with no change here.
 */
@Component
@ConditionalOnProperty(name = "perimity.guard.clients.campus", havingValue = "http", matchIfMissing = true)
public class HttpCampusConfigClient implements CampusConfigClient {

    private static final Logger log = LoggerFactory.getLogger(HttpCampusConfigClient.class);
    private static final String KEY = "repeat_entry_result";

    private final RestClient campus;
    private final Duration ttl;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(RepeatEntryPolicy policy, Instant readAt) { }

    public HttpCampusConfigClient(@Qualifier("campusRestClient") RestClient campus,
                                  @Value("${perimity.services.config-cache-seconds:300}") long ttlSeconds) {
        this.campus = campus;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public RepeatEntryPolicy repeatEntryPolicy(Long campusId) {
        if (campusId == null) {
            return RepeatEntryPolicy.DEFAULT;
        }

        Cached hit = cache.get(campusId);
        if (hit != null && Duration.between(hit.readAt(), Instant.now()).compareTo(ttl) < 0) {
            return hit.policy();
        }

        RepeatEntryPolicy policy = fetch(campusId);
        cache.put(campusId, new Cached(policy, Instant.now()));
        return policy;
    }

    private RepeatEntryPolicy fetch(Long campusId) {
        try {
            ConfigEnvelope response = campus.get()
                    .uri("/api/campus/campuses/{id}/config/{key}", campusId, KEY)
                    .retrieve()
                    .body(ConfigEnvelope.class);

            if (response == null || response.data() == null || response.data().configValue() == null) {
                return RepeatEntryPolicy.DEFAULT;
            }
            return RepeatEntryPolicy.parse(response.data().configValue());

        } catch (RuntimeException ex) {
            // debug, not warn. Until campus-service ships the key this fires on
            // every cache miss, and a log that cries wolf every five minutes is
            // a log nobody reads on the day it matters.
            log.debug("Could not read {} for campus {} ({}). Using default {}.",
                    KEY, campusId, ex.getMessage(), RepeatEntryPolicy.DEFAULT);
            return RepeatEntryPolicy.DEFAULT;
        }
    }

    /** Local wire shape. Only the field we read - Jackson ignores the rest. */
    record ConfigEnvelope(boolean success, String message, ConfigView data) { }

    record ConfigView(String configKey, String configValue) { }
}
