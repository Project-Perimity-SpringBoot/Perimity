package com.perimity.qr.service;

import com.perimity.qr.dto.QrGenerateRequest;
import com.perimity.qr.dto.QrInvalidateRequest;
import com.perimity.qr.dto.QrRecordResponse;
import com.perimity.qr.entity.QrRecord;
import com.perimity.qr.repository.QrRecordRepository;
import com.perimity.qr.storage.StorageService;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QrRecordService {

    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final QrRecordRepository qrRecordRepository;
    private final QrTokenService qrTokenService;
    private final QrImageService qrImageService;
    private final PdfDocumentService pdfDocumentService;
    private final StorageService storageService;

    public QrRecordService(QrRecordRepository qrRecordRepository,
                           QrTokenService qrTokenService,
                           QrImageService qrImageService,
                           PdfDocumentService pdfDocumentService,
                           StorageService storageService) {
        this.qrRecordRepository = qrRecordRepository;
        this.qrTokenService = qrTokenService;
        this.qrImageService = qrImageService;
        this.pdfDocumentService = pdfDocumentService;
        this.storageService = storageService;
    }

    /**
     * The active QR for a pass - what GET /api/qr/{passId} answers.
     *
     * Looks up by "active" rather than a straight passId match: a pass can
     * have older, invalidated QrRecord rows from a re-issue (see
     * QrRecord.invalidatedAt), and callers outside this service only ever
     * care about the current one.
     */
    @Transactional(readOnly = true)
    public QrRecordResponse getActiveByPassId(Long passId) {
        QrRecord qrRecord = qrRecordRepository.findByPassIdAndActiveTrue(passId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No active QR record for passId " + passId));

        return toResponse(qrRecord);
    }

    /**
     * The active QR for a pass, or empty. Day 8's retry path.
     *
     * A second, non-throwing form of getActiveByPassId rather than a change to
     * it. getActiveByPassId backs an HTTP GET, where "no QR yet" must be a 404,
     * and the queue consumer needs the same lookup where "no QR yet" is the
     * normal first-attempt state and not an error. Making one method serve both
     * would mean throwing an exception on the happy path of a retry and catching
     * it to decide what to do - control flow by exception, in the one place that
     * is already handling real failures.
     */
    @Transactional(readOnly = true)
    public Optional<QrRecordResponse> findActiveByPassId(Long passId) {
        return qrRecordRepository.findByPassIdAndActiveTrue(passId).map(this::toResponse);
    }

    /**
     * Creates the QR record for a pass and returns the plain token.
     *
     * The token is returned, never stored. Only sha256(token) reaches the
     * database, so this is the single moment the plain value exists - Day 6
     * takes it straight into the PNG. Nothing may log it.
     *
     * Any existing active QR for the pass is retired first. That is not
     * housekeeping: the unique constraint on qr_records covers token_hash
     * only, so two active rows for one pass is physically possible, and
     * findByPassIdAndActiveTrue returns an Optional. The second active row
     * would turn every subsequent read of that pass into an
     * IncorrectResultSizeDataAccessException - a 500 on the guard's screen,
     * appearing days later, nowhere near the re-issue that caused it.
     */
    @Transactional
    public GeneratedToken generate(QrGenerateRequest request) {
        qrRecordRepository.findByPassIdAndActiveTrue(request.getPassId())
                .ifPresent(existing -> {
                    existing.setActive(false);
                    existing.setInvalidatedAt(LocalDateTime.now());
                    existing.setInvalidatedReason("Superseded by re-issue");
                    qrRecordRepository.save(existing);
                });

        String token = qrTokenService.generateToken(request);
        String tokenHash = qrTokenService.hashToken(token);

        /*
         * A collision here is not realistically reachable - the IV is 96 random
         * bits per token, so two identical tokens is a broken SecureRandom, not
         * bad luck. The check stays because the alternative failure mode is a
         * unique-constraint violation surfacing as a 409 with no explanation,
         * and because a cheap read is worth the clarity of the error message.
         */
        if (qrRecordRepository.existsByTokenHash(tokenHash)) {
            throw new IllegalStateException(
                    "Token hash collision for passId " + request.getPassId());
        }

        QrRecord saved = qrRecordRepository.save(QrRecord.builder()
                .passId(request.getPassId())
                .campusId(request.getCampusId())
                .tokenHash(tokenHash)
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .active(true)
                .build());

        /*
         * Saved first, without keys, because the keys embed the row id and a
         * re-issue must not overwrite the previous pass's objects. The second
         * save runs inside the same transaction, so a failure while rendering
         * rolls the row back rather than leaving a QrRecord whose PNG was
         * never written - a pass that exists in the database and nowhere else
         * is worse than no pass at all.
         */
        byte[] qrPng = qrImageService.render(token);
        byte[] pdf = pdfDocumentService.render(request, qrPng);

        saved.setQrKey(storageService.put(
                objectKey(saved, "qr", "png"), qrPng, PNG_CONTENT_TYPE));
        saved.setPdfKey(storageService.put(
                objectKey(saved, "pdf", "pdf"), pdf, PDF_CONTENT_TYPE));

        return new GeneratedToken(token, toResponse(qrRecordRepository.save(saved)));
    }

    /**
     * Campus-prefixed object key: {campusId}/{kind}/{passId}/{recordId}.{ext}
     *
     * Campus first because Day 22 puts these in S3 under a campus prefix, and
     * a prefix that leads with the tenant is what makes a per-campus lifecycle
     * rule, access policy or bulk delete expressible at all. Record id last so
     * a re-issue writes a new object rather than overwriting the QR someone is
     * still carrying.
     *
     * Starts with a digit and contains no "..", so it satisfies
     * ValidationPatterns.OBJECT_KEY. Well under the 300-character column.
     */
    private String objectKey(QrRecord record, String kind, String extension) {
        return record.getCampusId() + "/" + kind + "/" + record.getPassId()
                + "/" + record.getId() + "." + extension;
    }

    /** Reads a stored object back. Backs the Day 6 download endpoint. */
    @Transactional(readOnly = true)
    public byte[] download(Long passId, boolean pdf) {
        QrRecord record = qrRecordRepository.findByPassIdAndActiveTrue(passId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No active QR record for passId " + passId));

        String key = pdf ? record.getPdfKey() : record.getQrKey();
        if (key == null) {
            throw new EntityNotFoundException(
                    "QR generation has not finished for passId " + passId);
        }
        return storageService.get(key);
    }

    /**
     * The plain token plus the persisted record.
     *
     * Deliberately not a DTO in the dto package: the plain token must never be
     * serialised into an HTTP response, and keeping this type inside the
     * service layer makes that hard to do by accident.
     */
    public record GeneratedToken(String token, QrRecordResponse record) {
    }

    /**
     * Retires the active QR for a pass - POST /api/internal/qr/invalidate/{passId}.
     *
     * Nothing is deleted. The row stays with active=false plus a timestamp and
     * a reason, because on Day 11 the guard's scan has to be able to answer
     * "this token was real, but it was replaced on Tuesday" rather than the
     * much less useful "no such token".
     *
     * Idempotent on purpose. gatepass-service calls this from a re-issue path
     * that can retry, and a retry must not come back 404 - that would read to
     * the caller as "the pass does not exist" and trigger a rollback of work
     * that in fact succeeded. So: no active row but history present means the
     * job is already done, and the existing record is returned unchanged.
     * Only a pass with no QR at all is a genuine 404.
     *
     * Note the reason is NOT overwritten on a repeat call. The first reason is
     * the one that describes what actually happened; a retry carrying the same
     * text would be harmless, but a later revoke overwriting an earlier
     * re-issue's reason would quietly destroy audit evidence.
     */
    @Transactional
    public QrRecordResponse invalidate(Long passId, QrInvalidateRequest request) {
        Optional<QrRecord> active = qrRecordRepository.findByPassIdAndActiveTrue(passId);

        if (active.isEmpty()) {
            List<QrRecord> history = qrRecordRepository.findByPassIdOrderByCreatedAtDesc(passId);
            if (history.isEmpty()) {
                throw new EntityNotFoundException("No QR record exists for passId " + passId);
            }
            return toResponse(history.get(0));
        }

        QrRecord qrRecord = active.get();
        qrRecord.setActive(false);
        /*
         * LocalDateTime.now() deliberately, not now(ZoneOffset.UTC): Hibernate's
         * @CreationTimestamp on this same row uses the JVM clock, so forcing UTC
         * here alone would put createdAt and invalidatedAt 5.5 hours apart on our
         * machines and make the audit trail read as if the QR was retired before
         * it was created. The UTC-vs-IST mismatch is real but it is a one-line
         * fix applied uniformly across all six services, not something to patch
         * inside a single method.
         */
        qrRecord.setInvalidatedAt(LocalDateTime.now());
        qrRecord.setInvalidatedReason(request.getReason());

        return toResponse(qrRecordRepository.save(qrRecord));
    }

    /**
     * Single mapping point from entity to response DTO.
     *
     * Extracted the moment a second caller appeared. Two hand-written copies
     * of this drift - one of them gains a field, the other does not, and the
     * same pass then looks different depending on which endpoint you ask.
     * Note what it does not copy: tokenHash and the row id never leave here.
     */
    private QrRecordResponse toResponse(QrRecord qrRecord) {
        return QrRecordResponse.builder()
                .passId(qrRecord.getPassId())
                .campusId(qrRecord.getCampusId())
                .qrKey(qrRecord.getQrKey())
                .pdfKey(qrRecord.getPdfKey())
                .validFrom(qrRecord.getValidFrom())
                .validTo(qrRecord.getValidTo())
                .active(qrRecord.isActive())
                .build();
    }
}
