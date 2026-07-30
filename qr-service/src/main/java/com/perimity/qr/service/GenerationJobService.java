package com.perimity.qr.service;

import com.perimity.qr.dto.BatchProgressResponse;
import com.perimity.qr.dto.JobStatusResponse;
import com.perimity.qr.messaging.contract.QrGenerationJob;
import com.perimity.qr.entity.GenerationJob;
import com.perimity.qr.entity.enums.EmailStatus;
import com.perimity.qr.entity.enums.JobStatus;
import com.perimity.qr.repository.GenerationJobRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GenerationJobService {

    /** generation_jobs.error_message is 1000 characters. */
    private static final int ERROR_MESSAGE_MAX = 1000;

    private final GenerationJobRepository generationJobRepository;

    public GenerationJobService(GenerationJobRepository generationJobRepository) {
        this.generationJobRepository = generationJobRepository;
    }

    /** What GET /api/qr/jobs/{jobId}/status answers. */
    @Transactional(readOnly = true)
    public JobStatusResponse getStatus(Long jobId) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No generation job with id " + jobId));

        return JobStatusResponse.builder()
                .jobId(job.getId())
                .passId(job.getPassId())
                .batchId(job.getBatchId())
                .status(job.getStatus())
                .retryCount(job.getRetryCount())
                .errorMessage(job.getErrorMessage())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .emailStatus(job.getEmailStatus())
                .emailError(job.getEmailError())
                .emailSentAt(job.getEmailSentAt())
                .build();
    }

    /**
     * What GET /api/qr/jobs/batch/{batchId}/progress answers - Arham's Bulk
     * Progress screen polls this.
     *
     * A batch with no rows is a 404, not a 0% bar. "Batch 999 does not exist"
     * and "batch 999 has not started" are different facts, and a progress bar
     * frozen at 0 cannot tell the user which one they are looking at.
     */
    @Transactional(readOnly = true)
    public BatchProgressResponse getBatchProgress(Long batchId) {
        long total = generationJobRepository.countByBatchId(batchId);
        if (total == 0) {
            throw new EntityNotFoundException("No generation jobs for batchId " + batchId);
        }

        long queued = generationJobRepository.countByBatchIdAndStatus(batchId, JobStatus.QUEUED);
        long processing = generationJobRepository.countByBatchIdAndStatus(batchId, JobStatus.PROCESSING);
        long done = generationJobRepository.countByBatchIdAndStatus(batchId, JobStatus.DONE);
        long failed = generationJobRepository.countByBatchIdAndStatus(batchId, JobStatus.FAILED);

        // Terminal means settled, not successful. A failed row is finished
        // being worked on, so it counts toward the bar - otherwise a batch
        // where every row failed would sit at 0% forever looking like a hang.
        long settled = done + failed;
        int percent = (int) ((settled * 100) / total);

        return BatchProgressResponse.builder()
                .batchId(batchId)
                .total(total)
                .queued(queued)
                .processing(processing)
                .done(done)
                .failed(failed)
                .percentComplete(percent)
                .finished(settled == total)
                .build();
    }


    /**
     * Takes ownership of a job message and returns the row to work on, or null
     * when the work is already finished.
     *
     * The idempotency key is Tushar's jobId, carried in the message body. That
     * is better than the AMQP message-id header an earlier version used, for one
     * concrete reason: he generates it once, at publish time, inside
     * buildJob() - so a republish of the same pass through his /republish
     * endpoint gets a NEW jobId and correctly regenerates, while a broker
     * redelivery of the same publish carries the SAME jobId and correctly does
     * not. A transport header cannot make that distinction, because it describes
     * the delivery rather than the intent.
     *
     * Three cases:
     *
     *   known jobRef, job DONE      -> null. A redelivery of finished work.
     *   known jobRef, job not DONE  -> that job, moved back to PROCESSING.
     *                                  retryCount untouched, so the listener can
     *                                  still tell this is a retry and reuse the
     *                                  QR it already produced.
     *   unknown jobRef              -> a new job row at retryCount 0.
     *
     * A blank jobId never reaches here - the listener rejects it to the DLQ,
     * because processing without an idempotency key risks issuing a second QR
     * and silently retiring a pass that was working.
     */
    @Transactional
    public GenerationJob claim(QrGenerationJob message) {
        Optional<GenerationJob> known =
                generationJobRepository.findByJobRef(message.jobId());

        if (known.isPresent()) {
            GenerationJob job = known.get();
            if (job.getStatus() == JobStatus.DONE) {
                return null;
            }
            job.setStatus(JobStatus.PROCESSING);
            job.setStartedAt(LocalDateTime.now());
            return generationJobRepository.save(job);
        }

        return generationJobRepository.save(GenerationJob.builder()
                .passId(message.passId())
                .batchId(message.batchId())
                .campusId(message.campusId())
                .jobRef(message.jobId())
                .status(JobStatus.PROCESSING)
                .startedAt(LocalDateTime.now())
                .build());
    }

    /**
     * The pass is ACTIVE and both objects are stored. Nothing else runs after
     * this for the job.
     *
     * errorMessage is cleared on purpose. A job that failed twice and succeeded
     * on the third attempt is a success, and leaving the second attempt's error
     * text on a DONE row makes it read like a failure to anyone triaging a batch
     * later.
     */
    @Transactional
    public void markDone(Long jobId) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No generation job with id " + jobId));

        job.setStatus(JobStatus.DONE);
        job.setCompletedAt(LocalDateTime.now());
        job.setErrorMessage(null);
        generationJobRepository.save(job);
    }

    /**
     * Records one failed attempt without settling the job.
     *
     * Status stays PROCESSING because more attempts are coming - only the
     * recoverer decides a job is FAILED. retryCount is what the listener reads
     * to know it is on a retry and may reuse the QR it already produced.
     *
     * REQUIRES_NEW: this must survive the failure that caused it. Called from a
     * catch block, it would otherwise be liable to join and then roll back with
     * a caller's transaction, and the one row explaining what went wrong is the
     * one row you cannot afford to lose. Nothing calls it inside a transaction
     * today; the annotation is there so that adding @Transactional to the
     * listener later cannot quietly break the audit trail.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(Long jobId, String errorMessage) {
        generationJobRepository.findById(jobId).ifPresent(job -> {
            job.setRetryCount(job.getRetryCount() + 1);
            job.setErrorMessage(truncate(errorMessage));
            generationJobRepository.save(job);
        });
    }

    /**
     * Settles a job as FAILED. Terminal - the message has gone to the DLQ.
     *
     * REQUIRES_NEW for the same reason as recordAttempt.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId, String errorMessage) {
        generationJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(truncate(errorMessage));
            generationJobRepository.save(job);
        });
    }

    /**
     * Marks the newest job for a pass FAILED. What FailedJobRecoverer calls,
     * which only has the message body and so only knows the passId.
     *
     * findFirstByPassIdOrderByIdDesc, not findByPassId: a re-issued pass has
     * more than one job row, and findByPassId returns an Optional, so it throws
     * IncorrectResultSizeDataAccessException the moment a second row exists.
     * Throwing from inside the recoverer would abandon the message without
     * dead-lettering it.
     *
     * Uses ifPresent rather than orElseThrow for the same reason - this runs on
     * the error path, and an error path that can itself fail is not an error
     * path.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedForPass(Long passId, String errorMessage) {
        generationJobRepository.findFirstByPassIdOrderByIdDesc(passId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(truncate(errorMessage));
            generationJobRepository.save(job);
        });
    }

    /**
     * Column length is enforced here rather than left to Postgres.
     *
     * An over-length value becomes a DataIntegrityViolationException thrown
     * while recording why something else already failed - a second failure that
     * hides the first. The truncation marker matters too: a silently cut message
     * reads as a complete one, so the reader trusts a sentence that was actually
     * chopped mid-clause.
     */
    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= ERROR_MESSAGE_MAX) {
            return value;
        }
        return value.substring(0, ERROR_MESSAGE_MAX - 3) + "...";
    }

    // ================= DAY 9 - pass email outcome =================

    /** generation_jobs.email_error is 500 characters. */
    private static final int EMAIL_ERROR_MAX = 500;

    /**
     * The current email state of a job, or null if the job is gone.
     *
     * Read by PassEmailService before it does any work, so that a broker
     * redelivery of an already-emailed job does not put a second copy of the
     * same pass in someone's inbox.
     */
    @Transactional(readOnly = true)
    public EmailStatus emailStatusOf(Long jobId) {
        return generationJobRepository.findById(jobId)
                .map(GenerationJob::getEmailStatus)
                .orElse(null);
    }

    /**
     * REQUIRES_NEW on all three writers below.
     *
     * They are called after the job has already been committed DONE and the
     * result message published. Joining a caller's transaction would let a
     * later rollback erase the only record of whether a visitor was ever told
     * about their pass - and unlike the pass itself, that is not reconstructable
     * from anything else.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEmailSent(Long jobId) {
        generationJobRepository.findById(jobId).ifPresent(job -> {
            job.setEmailStatus(EmailStatus.SENT);
            job.setEmailSentAt(LocalDateTime.now());
            job.setEmailError(null);
            generationJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEmailFailed(Long jobId, String reason) {
        generationJobRepository.findById(jobId).ifPresent(job -> {
            job.setEmailStatus(EmailStatus.FAILED);
            job.setEmailError(truncateEmailError(reason));
            generationJobRepository.save(job);
        });
    }

    /**
     * No address to send to. Not a failure, and deliberately not counted as one
     * - a walk-in visitor with no email is a normal bulk-upload row, and a red
     * number on the Bulk Progress screen for it would send someone chasing a
     * problem that does not exist.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEmailNotRequired(Long jobId) {
        generationJobRepository.findById(jobId).ifPresent(job -> {
            job.setEmailStatus(EmailStatus.NO_RECIPIENT);
            job.setEmailError(null);
            generationJobRepository.save(job);
        });
    }

    /**
     * Puts a settled email back to PENDING so it will be attempted again.
     * Backs the manual resend endpoint.
     *
     * Returns the job so the caller can read passId and batchId without a
     * second query.
     */
    @Transactional
    public GenerationJob resetEmailForRetry(Long jobId) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No generation job with id " + jobId));

        job.setEmailStatus(EmailStatus.PENDING);
        job.setEmailError(null);
        return generationJobRepository.save(job);
    }

    private String truncateEmailError(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= EMAIL_ERROR_MAX
                ? value
                : value.substring(0, EMAIL_ERROR_MAX - 3) + "...";
    }
}
