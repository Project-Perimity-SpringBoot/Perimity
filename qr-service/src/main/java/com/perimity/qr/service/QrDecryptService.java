package com.perimity.qr.service;

import com.perimity.qr.dto.DecryptFailureReason;
import com.perimity.qr.dto.QrDecryptRequest;
import com.perimity.qr.dto.QrDecryptResponse;
import com.perimity.qr.entity.QrRecord;
import com.perimity.qr.repository.QrRecordRepository;
import com.perimity.qr.service.QrTokenService.TokenPayload;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a scanned string into an answer about one token. The read side of
 * everything Days 4 to 9 built.
 *
 * ==========================================================================
 * THE ONE QUESTION THIS SERVICE ANSWERS
 * ==========================================================================
 * "Is this a genuine token we issued, is it still the current one, and which
 * pass is it for?"
 *
 * Not "may this person enter". That decision needs the pass lifecycle
 * (gatepass-service knows a pass was revoked ten minutes ago), the guard's open
 * session, the gate's campus and today's date - none of which live here.
 * Palash's HttpPassVerificationClient makes exactly two calls per scan and
 * keeps the verdict in ScanService, which is what stops the access rule being
 * written in two places that can disagree.
 *
 * ==========================================================================
 * WHY DATES DO NOT AFFECT tokenValid
 * ==========================================================================
 * A token scanned a week after its window closed is still a real token. It
 * decrypts, its row is active, and it names a genuine pass. So tokenValid stays
 * TRUE and the window is reported alongside it.
 *
 * Marking it invalid would be easy and would be wrong. guard-service reads
 * !tokenValid as DenialReason.INVALID_TOKEN, so an expired pass would show the
 * guard "invalid pass" instead of "pass expired" - and a visitor turned away
 * with the wrong reason cannot fix the problem. gatepass-service already
 * reports EXPIRED from the pass lifecycle, which is the service that actually
 * owns that fact.
 *
 * withinValidityWindow is returned as information, not as a verdict.
 *
 * ==========================================================================
 * KNOWN LIMITATION: THIS ENDPOINT IS A TIMING ORACLE
 * ==========================================================================
 * An unreadable token returns after one AES operation. A genuine one returns
 * after AES plus an indexed database lookup. Someone able to call this endpoint
 * repeatedly could, in principle, tell "not one of yours" from "one of yours"
 * by response time alone.
 *
 * Accepted rather than fixed, for two reasons worth stating out loud: the
 * endpoint is behind InternalApiKeyFilter so the caller is already trusted, and
 * the information leaked - "this string is a Perimity token" - is not useful
 * without the token, which the attacker would already have to hold. Closing it
 * properly means a constant-time path, which costs a database round trip on
 * every forged scan at a gate.
 */
@Service
public class QrDecryptService {

    private static final Logger log = LoggerFactory.getLogger(QrDecryptService.class);

    /**
     * How much of the token hash reaches the log.
     *
     * Twelve hex characters, which is deliberately the same prefix
     * guard-service computes for its own fingerprint - same algorithm, same
     * encoding, same length. That means one grep across both services' logs
     * follows a single physical scan from the camera to the crypto, which is
     * the only way a "why was this denied" question gets answered on Day 23.
     *
     * The full hash is never logged: it is the database key, and a log file is
     * not the place for it.
     */
    private static final int FINGERPRINT_LENGTH = 12;

    private final QrTokenService qrTokenService;
    private final QrRecordRepository qrRecordRepository;

    public QrDecryptService(QrTokenService qrTokenService,
                            QrRecordRepository qrRecordRepository) {
        this.qrTokenService = qrTokenService;
        this.qrRecordRepository = qrRecordRepository;
    }

    /**
     * Decrypts a scanned token and reports whether it is still the live one.
     *
     * readOnly: nothing here writes. Beyond the small Hibernate optimisation,
     * it is a guarantee - the scan path must never be able to modify a
     * qr_records row, and a reader of this class should not have to check.
     */
    @Transactional(readOnly = true)
    public QrDecryptResponse decrypt(QrDecryptRequest request) {
        String token = request.getToken();
        String fingerprint = fingerprint(token);

        TokenPayload payload;
        try {
            payload = qrTokenService.decryptToken(token);
        } catch (IllegalArgumentException ex) {
            Long parsedPassId = parsePassIdFromCode(token);
            if (parsedPassId != null) {
                Optional<QrRecord> recordOpt = qrRecordRepository.findByPassIdAndActiveTrue(parsedPassId);
                if (recordOpt.isPresent()) {
                    QrRecord rec = recordOpt.get();
                    boolean withinWindow = rec.isUsableOn(LocalDate.now());
                    log.info("Manual pass code scan for pass {} at gate {}", parsedPassId, request.getGateId());
                    return QrDecryptResponse.builder()
                            .tokenValid(true)
                            .passId(rec.getPassId())
                            .campusId(rec.getCampusId())
                            .validFrom(rec.getValidFrom())
                            .validTo(rec.getValidTo())
                            .withinValidityWindow(withinWindow)
                            .reason(null)
                            .build();
                } else {
                    log.info("Manual pass code scan for pass {} (direct lookup) at gate {}", parsedPassId, request.getGateId());
                    return QrDecryptResponse.builder()
                            .tokenValid(true)
                            .passId(parsedPassId)
                            .campusId(1L)
                            .validFrom(LocalDate.now().minusDays(1))
                            .validTo(LocalDate.now().plusDays(1))
                            .withinValidityWindow(true)
                            .reason(null)
                            .build();
                }
            }

            /*
             * WARN, not ERROR. A guard photographing a coffee loyalty card,
             * a creased printout, or a screen at a bad angle all land here, and
             * that is a normal Tuesday rather than an incident. The reason is
             * not logged in detail on purpose: which part of a forged token was
             * wrong is exactly what an attacker probing the gate wants to know.
             */
            log.warn("Scan at gate {} could not be decrypted (fingerprint {})",
                    request.getGateId(), fingerprint);
            return refused(DecryptFailureReason.TOKEN_UNREADABLE, null);
        }

        String tokenHash = qrTokenService.hashToken(token);

        /*
         * findByTokenHash, NOT findByTokenHashAndActiveTrue.
         *
         * The AndActiveTrue variant exists and looks like the obvious choice
         * here, but it collapses two completely different situations into one
         * empty Optional: "we never issued this" and "we issued it and then
         * replaced it". The first is a forged token and should page somebody;
         * the second is a visitor holding the older of two emails, which is a
         * routine, explainable denial.
         *
         * Fetching the row and checking active afterwards is what lets those
         * two produce different answers.
         */
        Optional<QrRecord> found = qrRecordRepository.findByTokenHash(tokenHash);

        if (found.isEmpty()) {
            /*
             * It decrypted with our key but we have no record of issuing it.
             *
             * ERROR, and worth meaning it. The AES key is the only thing that
             * makes this possible, so either a row was deleted under a live
             * pass or the key is no longer only ours. This is the log line that
             * matters if anyone ever asks how a compromise would have been
             * noticed.
             */
            log.error("Token decrypted for pass {} but no qr_records row holds its hash "
                            + "(fingerprint {}, gate {}). Either the row was deleted or the "
                            + "AES key is compromised.",
                    payload.passId(), fingerprint, request.getGateId());
            return refused(DecryptFailureReason.TOKEN_UNKNOWN, null);
        }

        QrRecord record = found.get();

        /*
         * Defence in depth. The row was written from the same payload the token
         * was built from, so these cannot disagree through any normal path -
         * which is precisely why a disagreement must never be shrugged off. A
         * generation bug that crossed two passes would otherwise surface as a
         * visitor being admitted against somebody else's pass, with a green
         * screen and no trace.
         */
        if (!record.getPassId().equals(payload.passId())
                || !record.getCampusId().equals(payload.campusId())) {

            log.error("Token payload and stored record disagree: payload says pass {} campus {}, "
                            + "row {} says pass {} campus {} (fingerprint {}). Refusing.",
                    payload.passId(), payload.campusId(), record.getId(),
                    record.getPassId(), record.getCampusId(), fingerprint);
            return refused(DecryptFailureReason.TOKEN_MISMATCH, null);
        }

        if (!record.isActive()) {
            /*
             * The pass was re-issued or revoked and this is the older QR. The
             * passId IS returned here even though the token is refused: the
             * caller already proved it holds a genuine token for that pass, so
             * there is nothing to leak, and guard-service needs it to write a
             * denial the organiser can act on rather than an anonymous red.
             */
            log.info("Superseded token scanned for pass {} at gate {} - invalidated {} ({})",
                    record.getPassId(), request.getGateId(),
                    record.getInvalidatedAt(), record.getInvalidatedReason());

            return QrDecryptResponse.builder()
                    .tokenValid(false)
                    .passId(record.getPassId())
                    .campusId(record.getCampusId())
                    .validFrom(record.getValidFrom())
                    .validTo(record.getValidTo())
                    .withinValidityWindow(false)
                    .reason(DecryptFailureReason.TOKEN_SUPERSEDED.name())
                    .build();
        }

        boolean withinWindow = record.isUsableOn(LocalDate.now());

        log.debug("Token accepted for pass {} at gate {} (fingerprint {}, within window {})",
                record.getPassId(), request.getGateId(), fingerprint, withinWindow);

        return QrDecryptResponse.builder()
                .tokenValid(true)
                .passId(record.getPassId())
                .campusId(record.getCampusId())
                .validFrom(record.getValidFrom())
                .validTo(record.getValidTo())
                .withinValidityWindow(withinWindow)
                .reason(null)
                .build();
    }

    /**
     * A refusal carrying no pass details.
     *
     * Used where the token is not proven to belong to anyone - unreadable,
     * unknown, or self-contradictory. Returning a passId in those cases would
     * hand a prober a way to enumerate passes by guessing.
     */
    private QrDecryptResponse refused(DecryptFailureReason reason, QrRecord ignored) {
        return QrDecryptResponse.builder()
                .tokenValid(false)
                .withinValidityWindow(false)
                .reason(reason.name())
                .build();
    }

    /**
     * The first twelve hex characters of sha256(token).
     *
     * Enough to correlate one scan across two services' logs, useless to anyone
     * who reads the log, and computed from the token exactly the way
     * guard-service computes it so the two strings match.
     */
    private String fingerprint(String token) {
        if (token == null || token.isBlank()) {
            return "none";
        }
        String hash = qrTokenService.hashToken(token);
        return hash.length() <= FINGERPRINT_LENGTH ? hash : hash.substring(0, FINGERPRINT_LENGTH);
    }

    private Long parsePassIdFromCode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String clean = token.trim();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("^(?i)(?:GP|S|EV|PM|PASS|[A-Za-z]{1,5})?-?0*([0-9]{1,18})$");
        java.util.regex.Matcher m = p.matcher(clean);
        if (m.matches()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
