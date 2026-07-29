package com.perimity.gatepass.client;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls into auth-service, user-service and campus-service.
 *
 * Three deliberate choices:
 *
 * 1. EVERY method returns Optional and never throws. These calls exist to
 *    enrich a QR job with a name and an email address. If campus-service is
 *    restarting, issuing a pass must still succeed - the pass is the important
 *    thing, the campus name on the PDF is not. A hard dependency here would
 *    mean any one service being down stops passes being issued anywhere.
 *
 * 2. A short timeout. A hung internal call must not hold an HTTP thread for
 *    thirty seconds; three seconds is far longer than a healthy call needs.
 *
 * 3. The internal API key travels on every request. These are /internal
 *    endpoints on the other side and they are not open to browsers.
 */
@Component
public class InternalServiceClient {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceClient.class);
    private static final String KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient auth;
    private final RestClient user;
    private final RestClient campus;

    public InternalServiceClient(
            RestClient.Builder builder,
            @Value("${perimity.services.auth-url}") String authUrl,
            @Value("${perimity.services.user-url}") String userUrl,
            @Value("${perimity.services.campus-url}") String campusUrl,
            @Value("${perimity.services.timeout-ms}") long timeoutMs,
            @Value("${perimity.internal.api-key}") String apiKey) {

        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.auth = builder.clone().baseUrl(authUrl).requestFactory(factory)
                .defaultHeader(KEY_HEADER, apiKey).build();
        this.user = builder.clone().baseUrl(userUrl).requestFactory(factory)
                .defaultHeader(KEY_HEADER, apiKey).build();
        this.campus = builder.clone().baseUrl(campusUrl).requestFactory(factory)
                .defaultHeader(KEY_HEADER, apiKey).build();
    }

    /** The holder's email, needed to send them their pass. */
    public Optional<String> emailOf(Long userId) {
        return get(auth, "/api/auth/internal/users/" + userId + "/email",
                EmailView.class, "auth").map(EmailView::email);
    }

    /** Campus name and code, for the PDF header and the storage prefix. */
    public Optional<CampusView> campusOf(Long campusId) {
        return get(campus, "/api/campus/campuses/" + campusId, CampusEnvelope.class, "campus")
                .map(CampusEnvelope::data);
    }

    /** Optional enrichment - a photo on the printed pass. */
    public Optional<ProfileView> profileOf(Long userId) {
        return get(user, "/api/user/internal/profiles/" + userId + "/summary",
                ProfileEnvelope.class, "user").map(ProfileEnvelope::data);
    }

    private <T> Optional<T> get(RestClient client, String path, Class<T> type, String service) {
        try {
            return Optional.ofNullable(client.get().uri(path)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .body(type));
        } catch (RestClientException ex) {
            // Warn, do not throw. See the class comment.
            log.warn("{}-service call {} failed, continuing without it: {}",
                    service, path, ex.getMessage());
            return Optional.empty();
        }
    }

    public record EmailView(String email) { }

    public record CampusView(Long id, String code, String name) { }

    public record CampusEnvelope(boolean success, CampusView data) { }

    public record ProfileView(Long userId, String identifierCode, String photoS3Key) { }

    public record ProfileEnvelope(boolean success, ProfileView data) { }
}
