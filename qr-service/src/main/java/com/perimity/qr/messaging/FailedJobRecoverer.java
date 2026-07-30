package com.perimity.qr.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.perimity.qr.messaging.contract.QrGenerationJob;
import com.perimity.qr.service.GenerationJobService;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

/**
 * Runs once, after the last retry of a message has failed.
 *
 * Spring Boot picks this up automatically: the listener container factory
 * configurer takes a MessageRecoverer bean when exactly one exists and uses it
 * in place of the default RejectAndDontRequeueRecoverer. Defining this class
 * IS the wiring - there is nothing to register.
 *
 * It does two things the default recoverer does not, and both are the
 * difference between a visible failure and an invisible one:
 *
 *   1. Settles the generation_jobs row as FAILED. Otherwise it sits at
 *      PROCESSING forever, so the Bulk Progress screen shows a batch stuck at
 *      97% with no failure count and percentComplete never reaches 100 - a
 *      PROCESSING job is not settled. That is precisely the hang the Day 17
 *      gate is written to catch.
 *
 *   2. Publishes a failure result to gatepass-service. Otherwise the pass sits
 *      at PENDING permanently, because gatepass waits to be told. Tushar's
 *      QrResultListener logs the failure and deliberately leaves the pass
 *      PENDING, which is right - an ACTIVE pass with no QR scans green while
 *      the holder has nothing to show.
 */
@Component
public class FailedJobRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(FailedJobRecoverer.class);

    private final ObjectMapper objectMapper;
    private final GenerationJobService generationJobService;
    private final QrResultPublisher resultPublisher;
    private final int maxAttempts;

    public FailedJobRecoverer(
            ObjectMapper objectMapper,
            GenerationJobService generationJobService,
            QrResultPublisher resultPublisher,
            @org.springframework.beans.factory.annotation.Value(
                    "${spring.rabbitmq.listener.simple.retry.max-attempts:3}") int maxAttempts) {

        this.objectMapper = objectMapper;
        this.generationJobService = generationJobService;
        this.resultPublisher = resultPublisher;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        QrGenerationJob job = readJob(message);
        String reason = describe(cause);

        if (job != null && job.passId() != null) {
            generationJobService.markFailedForPass(job.passId(), reason);
            resultPublisher.publishFailure(
                    job.jobId(), job.passId(), job.batchId(), reason, maxAttempts);
        }

        log.error("Retries exhausted for qr.generate.request (job {}, pass {}). Routing to {}. "
                        + "Inspect it at http://localhost:15672 and republish once the cause "
                        + "is fixed.",
                job == null ? null : job.jobId(),
                job == null ? null : job.passId(),
                RabbitConfig.QUEUE_DLQ,
                cause);

        /*
         * The throw is mandatory, not stylistic.
         *
         * A MessageRecoverer that returns normally is treated as having handled
         * the message, so the container acknowledges it and it is discarded -
         * the dead-letter queue stays empty and the payload is gone.
         * AmqpRejectAndDontRequeueException is what makes the broker reject it
         * so the queue's dead-letter arguments route it to qr.generate.dlq.
         *
         * This is the single easiest thing to get wrong in this file, and the
         * symptom is silent: everything looks correct except the DLQ never has
         * anything in it.
         */
        throw new AmqpRejectAndDontRequeueException("Retries exhausted for qr.generate.request", cause);
    }

    /**
     * Reads the job back out of the raw body.
     *
     * Deserialised with ObjectMapper directly rather than through the
     * MessageConverter: the converter infers its target type from the listener
     * method signature, and there is no method signature here, so it would fall
     * back to a LinkedHashMap and the cast would fail inside the error path -
     * the worst possible place to introduce a second failure.
     *
     * An unreadable body is survivable. The message still reaches the DLQ; only
     * the FAILED row and the failure result are missed, and a body that cannot
     * be parsed probably had no valid passId to write a row against anyway.
     */
    private QrGenerationJob readJob(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), QrGenerationJob.class);
        } catch (IOException ex) {
            log.error("Could not read the body of an exhausted qr.generate.request; "
                    + "no job row will be marked FAILED and gatepass will not be told", ex);
            return null;
        }
    }

    private String describe(Throwable cause) {
        String message = cause.getMessage() == null ? "no detail" : cause.getMessage();
        return "Retries exhausted - " + cause.getClass().getSimpleName() + ": " + message;
    }
}
