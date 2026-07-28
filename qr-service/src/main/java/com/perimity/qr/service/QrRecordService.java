package com.perimity.qr.service;

import com.perimity.qr.dto.QrInvalidateRequest;
import com.perimity.qr.dto.QrRecordResponse;
import com.perimity.qr.entity.QrRecord;
import com.perimity.qr.repository.QrRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QrRecordService {

    private final QrRecordRepository qrRecordRepository;

    public QrRecordService(QrRecordRepository qrRecordRepository) {
        this.qrRecordRepository = qrRecordRepository;
    }

    /**
     * The active QR for a pass - what GET /api/qr/{passId} answers.
     *
     * Looks up by "active" rather than a straight passId match: a pass can
     * have older, invalidated QrRecord rows from a re-issue, and callers
     * outside this service only ever care about the current one.
     */
    @Transactional(readOnly = true)
    public QrRecordResponse getActiveByPassId(Long passId) {
        QrRecord qrRecord = qrRecordRepository.findByPassIdAndActiveTrue(passId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No active QR record for passId " + passId));

        return toResponse(qrRecord);
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
     * The reason is NOT overwritten on a repeat call. The first reason is the
     * one that describes what actually happened; a later revoke overwriting an
     * earlier re-issue's reason would quietly destroy audit evidence.
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
         * CreationTimestamp on this same row uses the JVM clock, so forcing UTC
         * here alone would put createdAt and invalidatedAt 5.5 hours apart on our
         * machines and make the audit trail read as if the QR was retired before
         * it was created. The UTC-vs-IST mismatch is real but it is one uniform
         * fix across all six services, not a patch inside a single method.
         */
        qrRecord.setInvalidatedAt(LocalDateTime.now());
        qrRecord.setInvalidatedReason(request.getReason());

        return toResponse(qrRecordRepository.save(qrRecord));
    }

    /**
     * Single mapping point from entity to response DTO.
     *
     * Extracted the moment a second caller appeared. Two hand-written copies
     * drift - one gains a field, the other does not, and the same pass then
     * looks different depending on which endpoint you ask. Note what it does
     * not copy: tokenHash and the row id never leave here.
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