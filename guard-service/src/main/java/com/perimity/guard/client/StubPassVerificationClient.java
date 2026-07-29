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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT ONLY. Delete this class on Day 8.
 *
 * Real verification needs qr-service to decrypt the token and gatepass-service
 * to report the pass's current status. Neither call exists yet, and waiting for
 * them would leave the entire scan flow untestable for days.
 *
 * So this decodes a deliberately obvious plain-text development token:
 *
 *   dev:passId:holderUserId:Holder_Name:campusId:DAILY|EVENT:eventId:from:to
 *
 *   dev:118:108:Rohit_Kulkarni:1:DAILY::2026-07-01:2026-12-31
 *   dev:119:301:Sameer_Rao:1:EVENT:17:2026-08-10:2026-08-12
 *
 * Underscores stand in for spaces. ScanRequestDto's token pattern rejects
 * spaces - correctly, since no real QR payload contains one - so the dev
 * format has to live inside the same character set a real token would.
 *
 * The "dev:" prefix means a real encrypted token can never be mistaken for one
 * of these, and any token that does not start with it is refused as
 * INVALID_TOKEN - which is also exactly the right behaviour in production.
 *
 * @ConditionalOnMissingBean so that dropping in the real client on Day 8
 * silently retires this one. Removing the file is still the right move.
 */
@Component
@ConditionalOnMissingBean(ignored = StubPassVerificationClient.class, value = PassVerificationClient.class)
public class StubPassVerificationClient implements PassVerificationClient {

    private static final Logger log = LoggerFactory.getLogger(StubPassVerificationClient.class);
    private static final String DEV_PREFIX = "dev:";

    public StubPassVerificationClient() {
        log.warn("=======================================================================");
        log.warn(" StubPassVerificationClient is active. Development tokens only.");
        log.warn(" Replace with a real qr-service call on Day 8 and DELETE this class.");
        log.warn("=======================================================================");
    }

    @Override
    public PassVerification verify(String token) {
        String fingerprint = fingerprint(token);

        if (token == null || !token.startsWith(DEV_PREFIX)) {
            return PassVerification.undecodable(fingerprint);
        }

        String[] p = token.split(":", -1);
        if (p.length < 9) {
            return PassVerification.undecodable(fingerprint);
        }

        try {
            PassType type = PassType.valueOf(p[5]);
            return new PassVerification(
                    true,
                    statusOf(p[1]),
                    Long.valueOf(p[1]),
                    Long.valueOf(p[2]),
                    p[3].replace('_', ' '),
                    Long.valueOf(p[4]),
                    type,
                    p[6].isBlank() ? null : Long.valueOf(p[6]),
                    LocalDate.parse(p[7]),
                    p[8].isBlank() ? null : LocalDate.parse(p[8]),
                    fingerprint);
        } catch (RuntimeException ex) {
            return PassVerification.undecodable(fingerprint);
        }
    }

    /**
     * Lets the team exercise every denial path without a database.
     * passId 900-903 map to the four status refusals.
     */
    private DenialReason statusOf(String passId) {
        return switch (passId) {
            case "900" -> DenialReason.PASS_EXPIRED;
            case "901" -> DenialReason.PASS_REVOKED;
            case "902" -> DenialReason.PASS_PAUSED;
            case "903" -> DenialReason.PASS_PENDING;
            default -> null;
        };
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
}
