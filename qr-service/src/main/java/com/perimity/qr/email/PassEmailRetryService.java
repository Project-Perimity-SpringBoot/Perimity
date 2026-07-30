package com.perimity.qr.email;

import com.perimity.qr.entity.GenerationJob;
import com.perimity.qr.entity.enums.EmailStatus;
import com.perimity.qr.messaging.contract.QrGenerationJob;
import com.perimity.qr.repository.GenerationJobRepository;
import com.perimity.qr.repository.QrRecordRepository;
import com.perimity.qr.entity.QrRecord;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resends a pass email that failed, without regenerating anything.
 *
 * This is the repair tool for the failure mode Day 9 introduces: a mail server
 * that was down for ten minutes during a bulk upload leaves a batch of people
 * holding passes nobody told them about. The passes are fine. Only the
 * notification is missing, and regenerating to fix that would issue new tokens
 * and invalidate the QRs that already work.
 *
 * ONE LIMITATION, STATED RATHER THAN HIDDEN
 *
 * The recipient address and the wording live only in the original queue
 * message, which is gone by the time a retry happens - qr-service deliberately
 * does not persist holder emails, and should not start doing so to make a retry
 * convenient. So the caller supplies the address, which in practice means
 * gatepass-service republishing the job through its own /republish endpoint,
 * or an admin passing it in.
 *
 * The alternative - storing every visitor's email in qr_records so a resend can
 * find it - would put personal data in a second service for the sake of an
 * error path. Not worth it. This is a decision about data minimisation, not an
 * oversight, and it is worth saying so in a viva if anyone asks why the resend
 * needs an address handed to it.
 */
@Service
public class PassEmailRetryService {

    private static final Logger log = LoggerFactory.getLogger(PassEmailRetryService.class);

    private final GenerationJobRepository generationJobRepository;
    private final QrRecordRepository qrRecordRepository;
    private final PassEmailService passEmailService;

    public PassEmailRetryService(GenerationJobRepository generationJobRepository,
                                 QrRecordRepository qrRecordRepository,
                                 PassEmailService passEmailService) {
        this.generationJobRepository = generationJobRepository;
        this.qrRecordRepository = qrRecordRepository;
        this.passEmailService = passEmailService;
    }

    /**
     * Resends the pass email for one pass.
     *
     * Rebuilds the minimum QrGenerationJob PassEmailService needs - address,
     * subject, greeting - rather than requiring the full original contract
     * object. Everything else on that record is generation input and is
     * irrelevant to a resend.
     *
     * @return the email status after the attempt
     */
    public EmailStatus resend(Long passId, String recipient, String subject, String body) {
        GenerationJob job = generationJobRepository.findFirstByPassIdOrderByIdDesc(passId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No generation job for passId " + passId));

        QrRecord qr = qrRecordRepository.findByPassIdAndActiveTrue(passId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No active QR record for passId " + passId
                        + " - there is nothing to attach, so generation must run first"));

        /*
         * Reset to PENDING before sending. Without this, PassEmailService's own
         * "already settled" guard would see SENT or NO_RECIPIENT and skip
         * immediately - the guard that makes redelivery safe would also make a
         * deliberate resend a no-op, and it would return success having done
         * nothing.
         */
        job.setEmailStatus(EmailStatus.PENDING);
        job.setEmailError(null);
        generationJobRepository.save(job);

        QrGenerationJob rebuilt = new QrGenerationJob(
                job.getJobRef(), passId, job.getCampusId(), null, null, job.getBatchId(),
                null, null, recipient,
                null, null, null,
                qr.getValidFrom(), qr.getValidTo(),
                null, subject, body, null);

        passEmailService.sendPassEmail(job.getId(), rebuilt, qr.getPdfKey());

        EmailStatus result = generationJobRepository.findById(job.getId())
                .map(GenerationJob::getEmailStatus)
                .orElse(EmailStatus.FAILED);

        log.info("Resend for pass {} (job {}) finished with {}", passId, job.getId(), result);
        return result;
    }

    /** Every job whose email failed, oldest first. Backs the Day 10 bulk resend. */
    public List<GenerationJob> failedEmails() {
        return generationJobRepository.findByEmailStatusOrderByIdAsc(EmailStatus.FAILED);
    }
}
