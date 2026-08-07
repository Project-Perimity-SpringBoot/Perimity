package com.perimity.gatepass.messaging.contract;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * THE CONTRACT between gatepass-service and qr-service. Sanjay codes against
 * this exact shape - agree any change with him before making it.
 *
 * Day 10: batchId added, so the two copies now match. Sanjay had already added
 * it on his side and left a note asking for it here.
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

        /*
         * The holder's department NAME, for the DEPARTMENT field on the PDF.
         *
         * qr-service has rendered that field since Day 6 and has been printing
         * a dash on every pass ever issued, because this field did not exist
         * and its QrGenerationRequest.departmentName was therefore never set.
         *
         * Null is legitimate and stays a dash: a visitor has no department, and
         * neither does a student before staff assign one.
         */
        String departmentName,

        String passType,
        Long eventId,
        String eventName,

        /*
         * Null for a single approval, set for every row of a bulk upload.
         *
         * Sanjay's consumer has read this field since Day 8 and has been
         * receiving null, because it was missing HERE. That is what kept
         * generation_jobs.batch_id empty, countByBatchId returning zero, and
         * the Bulk Progress screen with nothing to bind to.
         *
         * Field ORDER is irrelevant - Jackson matches records by property
         * name, not position - but keeping it beside eventId keeps the two
         * copies of this contract readable side by side.
         */
        Long batchId,

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
