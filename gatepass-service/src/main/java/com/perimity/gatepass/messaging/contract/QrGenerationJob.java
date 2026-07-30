package com.perimity.gatepass.messaging.contract;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * THE CONTRACT between gatepass-service and qr-service. Sanjay codes against
 * this exact shape - agree any change with him before making it.
 *
 * Everything qr-service needs to generate the QR, render the PDF and send the
 * email is here. That is deliberate: the consumer must not have to call back
 * into gatepass, user or campus to finish its work. A job that needs three
 * follow-up HTTP calls is a job that fails when any of those services is down,
 * which defeats the point of putting it on a queue.
 *
 * jobId is a UUID generated at publish time. qr-service must treat it as an
 * idempotency key - RabbitMQ guarantees at-least-once delivery, so the same
 * job WILL sometimes arrive twice, and a visitor must not receive two emails
 * with two different QRs.
 */
public record QrGenerationJob(
        String jobId,
        Long passId,
        Long campusId,
        String campusName,
        String campusCode,

        Long holderUserId,
        String holderName,
        String holderEmail,

        String passType,
        Long eventId,
        String eventName,

        LocalDate validFrom,
        LocalDate validTo,

        String gateName,
        String emailSubject,
        String emailGreeting,
        LocalDateTime issuedAt
) {

    /** Storage prefix qr-service should write under: campus-code/passes/... */
    public String objectPrefix() {
        return (campusCode == null ? "campus-" + campusId : campusCode) + "/passes";
    }
}
