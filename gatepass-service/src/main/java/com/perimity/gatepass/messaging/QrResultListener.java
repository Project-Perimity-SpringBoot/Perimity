package com.perimity.gatepass.messaging;

import com.perimity.gatepass.dto.request.PassActivationDto;
import com.perimity.gatepass.messaging.contract.QrGenerationResult;
import com.perimity.gatepass.service.GatePassService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * qr-service reports back and the pass turns green.
 *
 * This is the second entry point to GatePassService.activate - the first being
 * the internal REST endpoint. Both call the same method, so there is one code
 * path with two doors. The REST door stays for manual testing and for a retry
 * by hand; this one is what runs in production.
 *
 * activate() is already idempotent, which matters here: RabbitMQ guarantees
 * at-least-once delivery, so this listener WILL occasionally see the same
 * result twice.
 */
@Component
public class QrResultListener {

    private static final Logger log = LoggerFactory.getLogger(QrResultListener.class);

    private final GatePassService gatePassService;

    public QrResultListener(GatePassService gatePassService) {
        this.gatePassService = gatePassService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_RESULT)
    public void onResult(QrGenerationResult result) {
        if (result == null || result.passId() == null) {
            log.error("Malformed QR result received, discarding: {}", result);
            return;
        }

        if (!result.success()) {
            // Left at PENDING on purpose. An ACTIVE pass with no QR would scan
            // green at the gate while the holder has nothing to show.
            log.error("QR generation FAILED for pass {} after attempt {} - {} "
                            + "(pass stays PENDING)",
                    result.passId(), result.attempt(), result.failureReason());
            return;
        }

        try {
            gatePassService.activate(result.passId(),
                    PassActivationDto.builder()
                            .qrKey(result.qrKey())
                            .pdfKey(result.pdfKey())
                            .build());

            log.info("Pass {} activated from job {}", result.passId(), result.jobId());

        } catch (RuntimeException ex) {
            // Most likely the pass was revoked while generation was running.
            // Not a broker problem, so do not retry - log and move on.
            log.error("Could not activate pass {} from job {}: {}",
                    result.passId(), result.jobId(), ex.getMessage());
        }
    }
}
