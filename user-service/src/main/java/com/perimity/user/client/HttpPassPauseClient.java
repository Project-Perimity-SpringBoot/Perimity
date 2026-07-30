package com.perimity.user.client;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The real call:
 *
 *     POST {base-url}/api/gatepass/internal/passes/holder/{holderUserId}/pause
 *     X-Internal-Api-Key: <INTERNAL_API_KEY>
 *     { "reason": "...", "changedBy": 108 }
 *
 * That endpoint is INTERNAL on gatepass-service's side, which is why this
 * carries the shared internal key rather than the caller's JWT. The two answer
 * different questions:
 *
 *     JWT           - which human is this, and what role do they have
 *     internal key  - is this one of our own six services
 *
 * The human who made the edit still travels, but as changedBy in the body,
 * where it belongs: it is data about the edit, not proof of who is calling.
 *
 * TIMEOUTS ARE NOT OPTIONAL. Without them a hung gatepass-service holds this
 * request thread until the socket gives up, and a profile save appears to
 * freeze for minutes.
 *
 * DAY 12: the value now comes from perimity.services.timeout-ms, the same
 * property gatepass and guard read, instead of being hardcoded here. One number
 * in .env tunes every internal call in the platform - and three services each
 * inventing their own is how one of them ends up at thirty seconds without
 * anybody noticing.
 */
public class HttpPassPauseClient implements PassPauseClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPassPauseClient.class);
    private static final String HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String internalApiKey;

    public HttpPassPauseClient(String baseUrl, long timeoutMs, String internalApiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.internalApiKey = internalApiKey;

        log.info("Pass pause calls will go to {}", baseUrl);
    }

    @Override
    public boolean pauseAllForHolder(Long holderUserId, String reason, Long changedBy) {
        try {
            restClient.post()
                    .uri("/api/gatepass/internal/passes/holder/{holderUserId}/pause", holderUserId)
                    .header(HEADER, internalApiKey)
                    .body(Map.of("reason", reason, "changedBy", changedBy))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Paused passes for holder {} - {}", holderUserId, reason);
            return true;

        } catch (RuntimeException ex) {
            // Deliberately swallowed. See PassPauseClient's Javadoc: the profile
            // edit is already committed, so failing the response here would tell
            // the user their save did not work when it did.
            log.error("Could not pause passes for holder {} after a sensitive edit ({}). "
                            + "The pass may still be ACTIVE and needs manual review: {}",
                    holderUserId, reason, ex.getMessage());
            return false;
        }
    }
}
