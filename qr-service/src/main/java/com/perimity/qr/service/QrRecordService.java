package com.perimity.qr.service;

import com.perimity.qr.dto.QrRecordResponse;
import com.perimity.qr.entity.QrRecord;
import com.perimity.qr.repository.QrRecordRepository;
import jakarta.persistence.EntityNotFoundException;
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
     * have older, invalidated QrRecord rows from a re-issue (see
     * QrRecord.invalidatedAt), and callers outside this service only ever
     * care about the current one.
     */
    @Transactional(readOnly = true)
    public QrRecordResponse getActiveByPassId(Long passId) {
        QrRecord qrRecord = qrRecordRepository.findByPassIdAndActiveTrue(passId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No active QR record for passId " + passId));

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
