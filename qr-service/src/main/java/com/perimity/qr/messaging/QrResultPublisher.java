package com.perimity.qr.messaging;

import com.perimity.qr.messaging.contract.QrGenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the settled outcome of a job onto qr.generate.result.
 *
 * Replaces the HTTP call into gatepass-service that an earlier version of this
 * service made. Tushar's queue-based reply is better and the reason is
 * concrete: an HTTP callback that hits a restarting gatepass-service fails,
 * retries, and eventually marks the job FAILED - even though the QR and the PDF
 * were generated correctly and are sitting in storage. A message waits.
 *
 * Every path out of the listener publishes exactly one result, success or
 * failure. That invariant is the whole value of this class: gatepass-service
 * leaves a pass at PENDING until it hears something, so a job that settles
 * silently is a pass that is stuck forever with no error anywhere.
 */
@Component
public class QrResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(QrResultPublisher.class);

    private final RabbitTemplate rabbit;

    public QrResultPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publishSuccess(String jobId, Long passId, Long batchId,
                               String qrKey, String pdfKey, int attempt) {
        publish(QrGenerationResult.ok(jobId, passId, batchId, qrKey, pdfKey, attempt));
    }

    public void publishFailure(String jobId, Long passId, Long batchId,
                               String reason, int attempt) {
        publish(QrGenerationResult.failed(jobId, passId, batchId, reason, attempt));
    }

    /**
     * Never throws.
     *
     * This is called from the listener's success path and from both of its
     * failure paths, including inside the recoverer. An exception here on the
     * failure path would replace a precise reason ("pass was revoked") with a
     * broker error, and on the success path it would turn completed work into a
     * retry that regenerates a QR that was already fine.
     *
     * The generation_jobs row is the durable record either way, so a lost
     * result message is recoverable - the job's status and error_message are
     * already committed, and Tushar's /republish endpoint can drive it again.
     * That is why swallowing is acceptable HERE specifically, and is not a
     * general licence to swallow: the state was persisted before this call.
     */
    private void publish(QrGenerationResult result) {
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_RESULT, result);

            log.info("Result published for job {} pass {} success={} attempt={}",
                    result.jobId(), result.passId(), result.success(), result.attempt());

        } catch (RuntimeException ex) {
            log.error("Could not publish the result for job {} pass {} - the pass will stay "
                            + "PENDING until it is republished. generation_jobs already "
                            + "records status and reason.",
                    result.jobId(), result.passId(), ex);
        }
    }
}
