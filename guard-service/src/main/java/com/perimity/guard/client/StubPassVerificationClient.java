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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
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
 * ==========================================================================
 * DAY 8 - THE CONDITION CHANGED, AND THE OLD ONE WAS UNSOUND
 * ==========================================================================
 * This used to carry @ConditionalOnMissingBean. That annotation is built for
 * @Bean methods inside auto-configuration, where ordering is guaranteed. On a
 * plain @Component it is evaluated during scanning, in no defined order relative
 * to other components - so adding HttpPassVerificationClient could just as
 * easily have produced NoUniqueBeanDefinitionException on ScanService's
 * constructor as it could have retired this class.
 *
 * An explicit property is decidable at startup and reads the same to everyone:
 *
 *     perimity.guard.clients.verification=http   real calls (the default)
 *     perimity.guard.clients.verification=stub   this class, dev tokens only
 *
 * ==========================================================================
 * DAY 11 - TWO LOCKS, NOT ONE, AND HERE IS WHY
 * ==========================================================================
 * This class hands out valid passes to anyone who can type
 * "dev:118:108:Any_Name:1:DAILY::2026-01-01:". At a real gate that is not a
 * test double, it is a skeleton key.
 *
 * One property standing between a deployed gate and that is too thin. A stray
 * GUARD_CLIENTS=stub copied into an EC2 .env on Day 22 would enable it silently,
 * and the only sign would be a warning in a log nobody is tailing.
 *
 * So it now also requires the "dev" profile. Deployed environments do not set
 * one, so even a wrong property leaves this class unregistered - and the wrong
 * property then fails loudly instead, because no PassVerificationClient bean
 * exists and ScanService will not construct. Refusing to start is a very good
 * outcome for a misconfiguration of this kind.
 *
 * To use it locally: SPRING_PROFILES_ACTIVE=dev plus the property.
 *
 * Delete the file outright once the live integration test passes.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "perimity.guard.clients.verification", havingValue = "stub")
public class StubPassVerificationClient implements PassVerificationClient {

    private static final Logger log = LoggerFactory.getLogger(StubPassVerificationClient.class);
    private static final String DEV_PREFIX = "dev:";

    public StubPassVerificationClient() {
        log.warn("=======================================================================");
        log.warn(" StubPassVerificationClient is ACTIVE - any dev: token is accepted.");
        log.warn(" This must never run outside a developer machine.");
        log.warn(" qr-service /decrypt now exists: switch to");
        log.warn("   perimity.guard.clients.verification=http");
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
