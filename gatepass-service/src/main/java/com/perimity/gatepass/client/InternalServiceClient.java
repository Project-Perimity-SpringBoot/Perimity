package com.perimity.gatepass.client;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls into auth-service, user-service and campus-service.
 *
 * ==========================================================================
 *  DAY 10: THREE PATHS WERE WRONG. ALL THREE FAILED SILENTLY.
 * ==========================================================================
 *
 * Every method here returns Optional and never throws, which is the right
 * design - a campus name missing from a PDF must not stop a pass being issued.
 * The cost of that design is that a WRONG URL looks exactly like a service
 * being down, and nothing ever goes red. All three calls had been broken since
 * they were written and the pipeline looked healthy the whole time:
 *
 *   campus  was  /api/campus/campuses/{id}
 *           now  /api/campus/internal/campuses/{id}
 *           why  Arham's InternalApiKeyFilter has
 *                shouldNotFilter = !uri.startsWith("/api/campus/internal/"),
 *                so on the public path the API key was never read and
 *                .anyRequest().authenticated() returned 401. Result: no campus
 *                name and no campus code on any generated pass.
 *
 *   auth    was  /api/auth/internal/users/{id}/email
 *           now  /api/internal/auth/users/by-email  (+ a new endpoint needed)
 *           why  Omkar's controller is @RequestMapping("/api/internal/auth/users")
 *                - the segments are the other way round - and there is no
 *                /{id}/email endpoint on it at all. Result: 404 on every
 *                lookup, holderEmail always null, NO VISITOR EVER EMAILED.
 *
 *   user    was  /api/user/internal/profiles/{id}/summary
 *           now  unchanged, but Mukul has no internal controller yet.
 *           why  Nothing to call. Left in place deliberately so it starts
 *                working the moment he ships it. See ASK-TEAM/mukul.md.
 *
 * THE LESSON, worth saying at the viva: fail-soft is correct for availability
 * and dangerous for observability. The fix is not to make these throw - it is
 * that a warn-level log on every single call should have been noticed. Consider
 * a startup smoke-check that pings each peer once and logs loudly.
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

    // ------------------------------------------------------------- campus

    /**
     * Campus name and code, for the PDF header and the storage prefix.
     *
     * FIXED PATH. Was hitting the public controller and getting 401.
     */
    public Optional<CampusView> campusOf(Long campusId) {
        return get(campus, "/api/campus/internal/campuses/" + campusId,
                CampusEnvelope.class, "campus").map(CampusEnvelope::data);
    }

    /**
     * One campus policy value, read as an int.
     *
     * Used for bulk.upload.max.rows. The fallback is not a second source of
     * truth - it is what happens when campus-service is unreachable, and it is
     * deliberately the same number Arham seeds as the default.
     */
    public int configInt(Long campusId, String key, int fallback) {
        return get(campus, "/api/campus/internal/campuses/" + campusId + "/config/" + key,
                ConfigEnvelope.class, "campus")
                .map(ConfigEnvelope::data)
                .map(c -> c.asInt(fallback))
                .orElseGet(() -> {
                    log.warn("Campus config {} unavailable for campus {} - using {}",
                            key, campusId, fallback);
                    return fallback;
                });
    }

    /** Same, for a boolean setting such as approval.required. */
    public boolean configBoolean(Long campusId, String key, boolean fallback) {
        return get(campus, "/api/campus/internal/campuses/" + campusId + "/config/" + key,
                ConfigEnvelope.class, "campus")
                .map(ConfigEnvelope::data)
                .map(c -> c.asBoolean(fallback))
                .orElse(fallback);
    }

    // --------------------------------------------------------------- auth

    /**
     * The holder's email, needed to send them their pass.
     *
     * NEEDS A NEW ENDPOINT FROM OMKAR: GET /api/internal/auth/users/{id}/email.
     * See ASK-TEAM/omkar.md. Until he ships it this returns empty and the pass
     * is generated but not emailed - which is exactly what has been happening
     * since Day 8, just now it is visible in the log rather than silent.
     */
    public Optional<String> emailOf(Long userId) {
        return get(auth, "/api/internal/auth/users/" + userId + "/email",
                EmailEnvelope.class, "auth").map(EmailEnvelope::data).map(EmailView::email);
    }

    /**
     * Does an identity already exist for this email?
     *
     * Omkar's endpoint 404s when it does not, which is not an error condition
     * here - "this is a brand new person" is a perfectly normal answer for a
     * bulk row. Empty means either "no such user" or "auth is down", and the
     * caller treats both the same way.
     */
    public Optional<Long> findUserIdByEmail(String email) {
        return get(auth, "/api/internal/auth/users/by-email?email="
                        + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8),
                UserEnvelope.class, "auth")
                .map(UserEnvelope::data)
                .map(UserView::id);
    }

    /**
     * THE MIXED-ATTENDEE CALL. Resolve an email to an identity, creating a
     * lightweight VISITOR if it is new.
     *
     * Omkar's POST /api/internal/auth/users is idempotent and does both halves
     * behind one call, which is why the bulk engine does not do "check, then
     * create" itself - that pattern has a race in it, and two rows with the
     * same email arriving together would create two accounts.
     *
     * The endpoint can only ever mint a VISITOR. role is not a field a caller
     * can set, so a bulk upload can never be turned into a path that creates a
     * Campus Admin.
     *
     * source lands in auth's audit_logs, so months later it is possible to ask
     * "where did this identity come from" and get "gatepass-bulk-batch-88".
     */
    public Optional<Long> resolveOrCreateIdentity(String email, String name, String phone,
                                                  Long campusId, String source) {
        try {
            UserEnvelope body = auth.post()
                    .uri("/api/internal/auth/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "email", email,
                            "name", name,
                            "phone", phone == null ? "" : phone,
                            "campusId", campusId,
                            "source", source))
                    .retrieve()
                    .body(UserEnvelope.class);

            return Optional.ofNullable(body).map(UserEnvelope::data).map(UserView::id);

        } catch (RestClientException ex) {
            // Unlike the reads, this one being empty means the row cannot be
            // issued at all, so it is logged at error and the caller skips the
            // row rather than the whole batch.
            log.error("auth-service could not resolve an identity for {}: {}",
                    email, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Is this email barred at this campus?
     *
     * NEEDS A NEW ENDPOINT FROM OMKAR. BlocklistService.isBlocked(campusId,
     * email, phone) already exists but is only reachable behind
     * @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAMPUS_ADMIN')") on
     * /api/auth/blocklist, which a service-to-service call cannot satisfy - it
     * carries an API key, not a staff JWT.
     *
     * Optional.empty means "could not find out", which the validator treats as
     * "allow, and log". An empty here must NOT be read as "not blocked".
     */
    public Optional<Boolean> isBlocklisted(Long campusId, String email, String phone) {
        String uri = "/api/internal/auth/blocklist/check?campusId=" + campusId
                + "&email=" + java.net.URLEncoder.encode(email,
                        java.nio.charset.StandardCharsets.UTF_8);
        if (phone != null && !phone.isBlank()) {
            uri += "&phone=" + java.net.URLEncoder.encode(phone,
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        return get(auth, uri, BlockedEnvelope.class, "auth")
                .map(BlockedEnvelope::data)
                .map(BlockedView::blocked);
    }

    // --------------------------------------------------------------- user

    /**
     * Optional enrichment - a photo on the printed pass.
     *
     * Mukul has no internal controller yet, so this 404s today. Left wired so
     * it starts working with no change here the moment he ships it.
     */
    public Optional<ProfileView> profileOf(Long userId) {
        return get(user, "/api/user/internal/profiles/" + userId + "/summary",
                ProfileEnvelope.class, "user").map(ProfileEnvelope::data);
    }

    // ------------------------------------------------------------- plumbing

    private <T> Optional<T> get(RestClient client, String path, Class<T> type, String service) {
        try {
            return Optional.ofNullable(client.get().uri(path)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .body(type));
        } catch (RestClientException ex) {
            // Warn, do not throw. See the class comment - and note that this
            // line hid three broken URLs for two days.
            log.warn("{}-service call {} failed, continuing without it: {}",
                    service, path, ex.getMessage());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------- shapes

    /*
     * These records mirror only the FIELDS THIS SERVICE USES, not the full
     * response. Jackson ignores unknown properties by default in Spring Boot,
     * so auth-service adding a field to UserResponse cannot break this client.
     * Copying the whole DTO across the boundary would couple the two services
     * far harder than the HTTP call already does.
     */

    public record CampusView(Long id, String code, String name) { }

    public record CampusEnvelope(boolean success, CampusView data) { }

    public record ConfigView(String configKey, String configValue, String valueType) {
        public int asInt(int fallback) {
            try {
                return configValue == null ? fallback : Integer.parseInt(configValue.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        public boolean asBoolean(boolean fallback) {
            return configValue == null ? fallback : Boolean.parseBoolean(configValue.trim());
        }
    }

    public record ConfigEnvelope(boolean success, ConfigView data) { }

    public record EmailView(String email) { }

    public record EmailEnvelope(boolean success, EmailView data) { }

    public record UserView(Long id, String email, String name, Long campusId) { }

    public record UserEnvelope(boolean success, UserView data) { }

    public record BlockedView(boolean blocked, String reason) { }

    public record BlockedEnvelope(boolean success, BlockedView data) { }

    public record ProfileView(Long userId, String identifierCode, String photoS3Key) { }

    public record ProfileEnvelope(boolean success, ProfileView data) { }
}
