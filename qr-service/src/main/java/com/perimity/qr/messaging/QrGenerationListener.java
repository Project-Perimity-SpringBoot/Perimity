package com.perimity.qr.messaging;

import com.perimity.qr.dto.QrGenerateRequest;
import com.perimity.qr.email.PassEmailDispatcher;
import com.perimity.qr.dto.QrRecordResponse;
import com.perimity.qr.entity.GenerationJob;
import com.perimity.qr.messaging.contract.QrGenerationJob;
import com.perimity.qr.service.GenerationJobService;
import com.perimity.qr.service.QrRecordService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes qr.generate.request and runs the whole pipeline: token, QR PNG, PDF,
 * both uploads, then a result message back on qr.generate.result.
 *
 * NOT annotated @Transactional, and that is load-bearing rather than an
 * oversight. Each step writes its own transaction through the service layer. If
 * one transaction spanned this method, a failure late in it would roll back the
 * FAILED status and the error message written to explain it - so the one row
 * that tells you what went wrong would be the one row guaranteed to disappear.
 * A single long transaction would also hold a Postgres connection open across a
 * broker publish, which is how a connection pool dies under bulk load on Day 10.
 *
 * Every exit from this method publishes exactly one result. gatepass-service
 * leaves a pass at PENDING until it hears something, so a job that settles
 * silently is a pass stuck forever with nothing anywhere explaining why.
 *
 * Two failure paths, deliberately separate:
 *
 *   PermanentGenerationException - a failure retrying cannot fix. The job is
 *   marked FAILED, a failure result is published, and the message goes straight
 *   to qr.generate.dlq. No retries, because nothing about a retry would differ.
 *
 *   anything else - the attempt is recorded and the exception rethrown, so
 *   Spring's retry interceptor tries again with backoff. When the attempts run
 *   out, FailedJobRecoverer settles the job and publishes the failure.
 */
@Component
public class QrGenerationListener {

    private static final Logger log = LoggerFactory.getLogger(QrGenerationListener.class);

    private final Validator validator;
    private final GenerationJobService generationJobService;
    private final QrRecordService qrRecordService;
    private final QrResultPublisher resultPublisher;
    private final PassEmailDispatcher passEmailDispatcher;

    public QrGenerationListener(Validator validator,
                                GenerationJobService generationJobService,
                                QrRecordService qrRecordService,
                                QrResultPublisher resultPublisher,
                                PassEmailDispatcher passEmailDispatcher) {
        this.validator = validator;
        this.generationJobService = generationJobService;
        this.qrRecordService = qrRecordService;
        this.resultPublisher = resultPublisher;
        this.passEmailDispatcher = passEmailDispatcher;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_GENERATE)
    public void onGenerationJob(QrGenerationJob message) {

        QrGenerateRequest request = toGenerateRequest(message);

        GenerationJob job = generationJobService.claim(message);
        if (job == null) {
            /*
             * A jobId already carried to DONE. This is a redelivery, not new
             * work - the broker redelivers whenever a consumer dies between
             * finishing a job and acknowledging it, which is normal.
             *
             * Returning acknowledges it. Regenerating instead would retire the
             * QR the holder was already emailed and issue a new one, so a pass
             * that was working would stop working with nothing anywhere saying
             * why. That is exactly the harm Tushar's jobId exists to prevent.
             *
             * The result is republished rather than skipped. If the original
             * result message was lost - broker down at publish time, or this
             * service died between committing DONE and publishing - then
             * gatepass still holds the pass at PENDING, and staying quiet here
             * would leave it there permanently. activate() is idempotent on his
             * side, so a duplicate result costs nothing.
             */
            log.info("Job {} for pass {} is already DONE - republishing its result",
                    message.jobId(), message.passId());

            republishSettledResult(message);
            return;
        }

        // attempt is 1-based for a human reading it: retryCount 0 is attempt 1.
        int attempt = job.getRetryCount() + 1;

        try {
            QrRecordResponse record = obtainRecord(request, job);

            generationJobService.markDone(job.getId());

            /*
             * Published AFTER the job row is committed DONE, not before.
             *
             * The other order has a real failure mode: gatepass activates the
             * pass, this service dies before committing, and the redelivered
             * message finds a job that is not DONE - so it regenerates and
             * retires the QR the holder can already see. Commit first and the
             * worst case is a lost result message, which is recoverable because
             * the DONE row is the durable record.
             */
            resultPublisher.publishSuccess(message.jobId(), message.passId(), message.batchId(),
                    record.getQrKey(), record.getPdfKey(), attempt);

            log.info("Generation complete for pass {} (job {}, batch {}, attempt {})",
                    message.passId(), job.getId(), message.batchId(), attempt);

            /*
             * DAY 9. Last, and outside everything that can fail the job.
             *
             * Placed after markDone and after the result publish on purpose. By
             * this point the token exists, both objects are stored, and gatepass
             * has been told to activate - all correct, none of it conditional on
             * an email. sendPassEmail never throws; a mail failure is recorded
             * on the job row and retried separately, because retrying the JOB
             * would regenerate the QR, which is issuing a second token to repair
             * a mail problem.
             *
             * It also runs on this consumer thread, which is why the mail
             * timeouts in application.properties are bounded. Day 10 moves this
             * onto its own queue so 600 bulk sends do not hold the generation
             * pipeline.
             */
            /*
             * DAY 10. Queued, not sent, on this thread.
             *
             * Day 9 called PassEmailService directly here, which put a 50-200ms
             * SMTP round trip inside the listener method - so a job was not DONE
             * until the mail server answered, and 600 bulk rows meant the
             * progress bar stood still for a minute. The dispatcher hands the
             * work to a bounded executor and returns.
             *
             * Nothing is lost if this service dies before the send completes:
             * email_status stays PENDING and ReconciliationService reports it.
             */
            passEmailDispatcher.dispatch(job.getId(), message, record.getPdfKey());

        } catch (PermanentGenerationException ex) {
            generationJobService.markFailed(job.getId(), describe(ex));
            resultPublisher.publishFailure(message.jobId(), message.passId(),
                    message.batchId(), describe(ex), attempt);

            log.error("Permanent failure for pass {} (job {}) - routing to {}",
                    message.passId(), job.getId(), RabbitConfig.QUEUE_DLQ, ex);

            // This exception type is what stops the retry interceptor trying
            // again. A plain rethrow would spend three attempts reaching the
            // same conclusion.
            throw new AmqpRejectAndDontRequeueException(ex.getMessage(), ex);

        } catch (RuntimeException ex) {
            generationJobService.recordAttempt(job.getId(), describe(ex));

            log.warn("Transient failure for pass {} (job {}, attempt {}): {}",
                    message.passId(), job.getId(), attempt, ex.toString());

            // No result published here. More attempts are coming, and telling
            // gatepass "failed" now would be wrong - FailedJobRecoverer
            // publishes the failure only once the attempts are genuinely spent.
            throw ex;
        }
    }

    /**
     * Re-sends the outcome of a job that had already settled before this
     * redelivery arrived.
     *
     * Reads the stored QrRecord rather than trusting that the pass is fine: a
     * DONE job whose active QrRecord has since been invalidated by a revoke
     * means the keys in it are no longer the ones to hand over, and reporting
     * success would activate a pass whose QR was deliberately retired.
     */
    private void republishSettledResult(QrGenerationJob message) {
        Optional<QrRecordResponse> record =
                qrRecordService.findActiveByPassId(message.passId());

        if (record.isPresent()
                && record.get().getQrKey() != null
                && record.get().getPdfKey() != null) {

            resultPublisher.publishSuccess(message.jobId(), message.passId(), message.batchId(),
                    record.get().getQrKey(), record.get().getPdfKey(), 1);
            return;
        }

        resultPublisher.publishFailure(message.jobId(), message.passId(), message.batchId(),
                "Job was DONE but no active QR record remains - it was invalidated by a "
                        + "re-issue or a revoke after generation", 1);
    }

    /**
     * Maps the producer's wide job message onto the narrow input
     * QrRecordService already takes, and validates it.
     *
     * The mapping is the validation point, deliberately. QrGenerateRequest is
     * where the constraints live - @NotNull and @Positive on the ids, plus
     * @ValidDateRange across the two dates - and a @RabbitListener parameter is
     * NOT validated the way an @Valid controller body is. There is no @Valid
     * equivalent wired in by default, so without this every constraint would be
     * skipped silently: the same failure the team's standing @Valid rule warns
     * about, one layer over, and the first sign of it would be a QrRecord with a
     * null campusId.
     *
     * Everything the contract carries that generation does not need -
     * campusName, holderName, holderEmail, emailSubject, emailGreeting - is
     * dropped here rather than passed on. Day 9 reads those from the message
     * directly. The PDF stays anonymous until that design question is settled
     * with Tushar: his README expects the campus name on it, Day 6 deliberately
     * left it off, and quietly picking one would bury the disagreement.
     */
    private QrGenerateRequest toGenerateRequest(QrGenerationJob message) {
        if (message == null) {
            throw new AmqpRejectAndDontRequeueException("Empty qr.generate.request body");
        }

        /*
         * A missing jobId is rejected rather than tolerated.
         *
         * Processing without an idempotency key means a redelivery issues a
         * second QR and retires the first, so a visitor's working pass breaks
         * silently. A rejected message sits visibly in the DLQ with its payload
         * intact. Between a silent wrong answer and a loud missing one, take the
         * loud one.
         */
        if (message.jobId() == null || message.jobId().isBlank()) {
            log.error("qr.generate.request for pass {} has no jobId - rejecting to {}",
                    message.passId(), RabbitConfig.QUEUE_DLQ);
            throw new AmqpRejectAndDontRequeueException(
                    "qr.generate.request is missing jobId, the idempotency key");
        }

        QrGenerateRequest request = QrGenerateRequest.builder()
                .passId(message.passId())
                .campusId(message.campusId())
                .batchId(message.batchId())
                .holderUserId(message.holderUserId())
                .validFrom(message.validFrom())
                .validTo(message.validTo())
                .build();

        Set<ConstraintViolation<QrGenerateRequest>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return request;
        }

        String detail = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));

        log.error("Rejecting invalid qr.generate.request (job {}): {}", message.jobId(), detail);
        throw new AmqpRejectAndDontRequeueException(
                "Invalid qr.generate.request - " + detail);
    }

    /**
     * Produces the QR record, reusing the existing one when this is a retry.
     *
     * Gated on retryCount > 0. Without the check, an attempt that uploaded both
     * files and then failed on the publish would, on its next attempt, call
     * generate() again - retiring the QrRecord it just created, writing a new
     * token, and orphaning a PNG and a PDF in storage. Three attempts, three
     * tokens, two of them dead.
     *
     * Tushar's jobId turns this from a heuristic into a clean signal. A genuine
     * re-issue arrives with a different jobId and so gets a fresh job row at
     * retryCount 0, which correctly regenerates; only a real retry of the same
     * job finds its own row already carrying a failed attempt.
     */
    private QrRecordResponse obtainRecord(QrGenerateRequest request, GenerationJob job) {
        if (job.getRetryCount() > 0) {
            Optional<QrRecordResponse> existing =
                    qrRecordService.findActiveByPassId(request.getPassId());

            if (existing.isPresent()
                    && existing.get().getQrKey() != null
                    && existing.get().getPdfKey() != null) {

                log.info("Reusing the QR from an earlier attempt for pass {} (job {})",
                        request.getPassId(), job.getId());
                return existing.get();
            }
        }

        return qrRecordService.generate(request).record();
    }

    /**
     * A short, safe description for error_message and for failureReason.
     *
     * Type plus message, never a stack trace and never the cause chain:
     * error_message is 1000 characters and is read by a human triaging a failed
     * batch, not by a debugger. The full trace is already in the log with the
     * pass id beside it.
     */
    private String describe(Throwable cause) {
        String message = cause.getMessage() == null ? "no detail" : cause.getMessage();
        return cause.getClass().getSimpleName() + ": " + message;
    }
}
