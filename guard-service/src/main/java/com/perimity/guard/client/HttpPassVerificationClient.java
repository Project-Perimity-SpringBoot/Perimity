package com.perimity.guard.client;

import com.perimity.guard.document.enums.DenialReason;
import com.perimity.guard.document.enums.PassType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The real pass verification. Two hops, in this order, on every scan.
 *
 *   1. POST {qr}/api/internal/qr/decrypt      - is this a genuine, live token?
 *   2. GET  {gatepass}/api/internal/gatepass/passes/{id}
 *                                             - and may that pass enter today?
 *
 * ==========================================================================
 * WHY TWO SERVICES AND NOT ONE
 * ==========================================================================
 * qr-service owns cryptography and answers exactly one question: does this
 * string decrypt to a token we issued, and has that token been superseded.
 * gatepass-service owns the pass lifecycle and is the only place that knows a
 * pass was revoked ten minutes ago.
 *
 * A single call would put the access rule in one of those two services, and
 * QrDecryptResponse says so in its own comment: "whether that pass may enter
 * this gate today is guard-service's decision". Keeping the decision here is
 * also what makes ScanService readable as the gate logic rather than a caller of
 * somebody else's verdict.
 *
 * The cost is a second network hop per scan, which is exactly what the Redis
 * cache on Day 11 removes.
 *
 * ==========================================================================
 * WHY passType AND status ARRIVE AS STRINGS
 * ==========================================================================
 * Deserialising straight into this service's enums would mean that the day
 * Tushar adds a PassStatus value, every scan against the new value fails to
 * parse and the gate stops. Strings plus an explicit switch means an unknown
 * value is a logged, deliberate refusal instead of a 500 - and the six services
 * can be deployed in any order.
 */
@Component
@ConditionalOnProperty(name = "perimity.guard.clients.verification", havingValue = "http", matchIfMissing = true)
public class HttpPassVerificationClient implements PassVerificationClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPassVerificationClient.class);

    private final RestClient qr;
    private final RestClient gatepass;

    public HttpPassVerificationClient(@Qualifier("qrRestClient") RestClient qr,
                                      @Qualifier("gatepassRestClient") RestClient gatepass) {
        this.qr = qr;
        this.gatepass = gatepass;
        log.info("HttpPassVerificationClient active - real qr-service and gatepass-service calls.");
    }

    @Override
    public PassVerification verify(String token) {
        String fingerprint = fingerprint(token);

        if (token == null || token.isBlank()) {
            return PassVerification.undecodable(fingerprint);
        }

        DecryptEnvelope decrypted = call(
                () -> qr.post()
                        .uri("/api/internal/qr/decrypt")
                        .body(new DecryptRequest(token, null))
                        .retrieve()
                        .body(DecryptEnvelope.class),
                "qr-service decrypt");

        // A token that does not decrypt, or whose record was superseded by a
        // re-issue, is an invalid pass - a real answer, logged as a denial.
        if (decrypted == null || decrypted.data() == null || !decrypted.data().tokenValid()) {
            return PassVerification.undecodable(fingerprint);
        }

        DecryptView d = decrypted.data();

        PassEnvelope passEnvelope = call(
                () -> gatepass.get()
                        .uri("/api/internal/gatepass/passes/{id}", d.passId())
                        .retrieve()
                        .body(PassEnvelope.class),
                "gatepass-service pass lookup");

        // The token decrypted but the pass is gone. Not an outage - a token for a
        // pass that no longer exists is exactly an invalid pass.
        if (passEnvelope == null || passEnvelope.data() == null) {
            log.warn("Token decrypted to pass {} but gatepass-service has no such pass", d.passId());
            return PassVerification.undecodable(fingerprint);
        }

        PassView p = passEnvelope.data();

        return new PassVerification(
                true,
                denialFor(p.status()),
                p.id(),
                p.holderUserId(),
                p.holderName(),
                // campusId from the PASS, not from the token. ScanService compares
                // it against the session's campus, and the pass record is the
                // authority on which campus issued it.
                p.campusId(),
                passTypeFor(p.passType()),
                p.eventId(),
                // Dates from the pass too. qr_records carries a copy, but a
                // re-issue can change the window and the pass row is what the
                // lifecycle actually updates.
                p.validFrom(),
                p.validTo(),
                fingerprint);
    }

    /**
     * Maps gatepass's PassStatus onto our DenialReason. Null means "no objection
     * from the lifecycle" - ScanService still checks campus and date range after
     * this, so returning null is not the same as saying yes.
     */
    DenialReason denialFor(String status) {
        if (status == null) {
            return DenialReason.INVALID_TOKEN;
        }
        return switch (status) {
            case "ACTIVE"  -> null;
            case "PENDING" -> DenialReason.PASS_PENDING;
            case "PAUSED"  -> DenialReason.PASS_PAUSED;
            case "EXPIRED" -> DenialReason.PASS_EXPIRED;
            case "REVOKED" -> DenialReason.PASS_REVOKED;
            default -> {
                // A status this service has never heard of. Refuse, and say so
                // loudly - this is the log line that explains a mystery denial.
                log.error("Unknown PassStatus '{}' from gatepass-service. Refusing entry. "
                        + "guard-service needs updating.", status);
                yield DenialReason.INVALID_TOKEN;
            }
        };
    }

    PassType passTypeFor(String passType) {
        if ("EVENT".equals(passType)) {
            return PassType.EVENT;
        }
        if ("DAILY".equals(passType)) {
            return PassType.DAILY;
        }
        log.warn("Unknown pass type '{}' - treating as DAILY for attribution", passType);
        return PassType.DAILY;
    }

    /**
     * Any transport failure becomes an outage, never a denial.
     *
     * A timeout means we do not know whether the pass is valid. Turning "we do
     * not know" into a red card would put a refusal in the register against
     * someone who may hold a perfectly good pass.
     */
    private <T> T call(java.util.function.Supplier<T> request, String what) {
        try {
            return request.get();
        } catch (RuntimeException ex) {
            log.error("{} failed: {}", what, ex.getMessage());
            throw new PassVerificationUnavailableException(
                    "Cannot verify passes right now - " + what + " is unreachable.", ex);
        }
    }

    /** First 12 hex characters of the SHA-256. Enough to correlate, useless if leaked. */
    private String fingerprint(String token) {
        if (token == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ---------------------------------------------------------------------
    // Wire shapes. Local records on purpose: importing another service's DTO
    // would mean a shared jar, and a shared jar is how database-per-service
    // becomes package-per-service. Unknown fields are ignored by Jackson, so
    // these only list what guard-service actually reads.
    // ---------------------------------------------------------------------

    record DecryptRequest(String token, Long gateId) { }

    record DecryptEnvelope(boolean success, String message, DecryptView data) { }

    record DecryptView(boolean tokenValid, Long passId, Long campusId,
                       LocalDate validFrom, LocalDate validTo, String reason) { }

    record PassEnvelope(boolean success, String message, PassView data) { }

    record PassView(Long id, Long holderUserId, String holderName, Long campusId,
                    String passType, Long eventId, String status,
                    LocalDate validFrom, LocalDate validTo) { }
}
