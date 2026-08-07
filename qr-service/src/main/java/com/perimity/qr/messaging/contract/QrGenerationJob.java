package com.perimity.qr.messaging.contract;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ==========================================================================
 *  SHARED CONTRACT - owned by gatepass-service (Tushar).
 *
 *  Source of truth:
 *    gatepass-service/src/main/java/com/perimity/gatepass/messaging/contract/
 *        QrGenerationJob.java
 *
 *  This is a copy. Change only the package line. Any field change is agreed
 *  with Tushar first and applied to BOTH copies in the same commit.
 * ==========================================================================
 *
 * The producer owns the contract, which is why this file is a copy rather than
 * something negotiated: gatepass-service knows when a pass is issued and what
 * it is for, and the consumer's job is to accept that.
 *
 * Everything needed to generate the QR, render the PDF and send the email is
 * carried here. A job that needs three follow-up HTTP calls to complete is a
 * job that fails whenever any of those services is down, which defeats the
 * point of putting it on a queue in the first place.
 *
 * ONE ADDITION TO TUSHAR'S VERSION: batchId, below. It is not in his copy yet.
 * Raised with him; the field reads as null until he adds it, so nothing breaks
 * in the meantime and nothing needs changing here when he does.
 *
 * Deserialised by Jackson's native record support - no no-arg constructor and
 * no setters needed, and a component absent from the JSON arrives as null
 * rather than failing. That is what makes the batchId addition safe ahead of
 * the producer.
 */
public record QrGenerationJob(

        /*
         * The idempotency key, a UUID generated at publish time.
         *
         * RabbitMQ guarantees at-least-once delivery, so the same job WILL
         * sometimes arrive twice - the broker redelivers whenever a consumer
         * dies between finishing work and acknowledging it, which is a normal
         * event and not an error. Without this key a redelivery would issue a
         * second QR and retire the first, so a visitor's already-emailed pass
         * would silently stop working.
         */
        String jobId,

        Long passId,
        Long campusId,
        String campusName,
        String campusCode,

        /*
         * NOT IN TUSHAR'S COPY YET. Null for a single approval, set for every
         * row of a bulk upload.
         *
         * Without it, generation_jobs.batch_id is null on every row,
         * countByBatchId returns zero, BatchProgressResponse 404s, and the
         * Bulk Progress screen has nothing to bind to - which takes out the
         * Day 17 gate ("upload 600 rows, confirm, watch the progress bar
         * finish") outright.
         */
        Long batchId,

        Long holderUserId,
        String holderName,
        String holderEmail,

        /*
         * The holder's department name, resolved by gatepass-service from
         * user-service.
         *
         * PdfDocumentService has rendered a DEPARTMENT field since Day 6 and
         * has printed a dash on every pass, because this field did not exist on
         * either copy of the contract and toGenerateRequest therefore had
         * nothing to map. Null still prints the dash, which is right for a
         * visitor and for a student with no department set.
         */
        String departmentName,

        String passType,
        Long eventId,
        String eventName,

        LocalDate validFrom,
        LocalDate validTo,

        String gateName,

        /*
         * Email wording, composed by gatepass-service.
         *
         * The right split: gatepass knows whether this is a daily or an event
         * pass and what the event is called; qr-service knows how to render a
         * PDF and talk to SES. Changing the words never touches the service
         * that sends them.
         *
         * Unused on Day 8. Day 9 consumes both.
         */
        String emailSubject,
        String emailGreeting,

        LocalDateTime issuedAt
) {

    /**
     * Storage prefix the producer expects: campus-code/passes/...
     *
     * NOT USED on Day 8. qr-service currently writes
     * {campusId}/{kind}/{passId}/{recordId}.{ext} from QrRecordService, which
     * is campus-prefixed but by id rather than by code. Both satisfy the S3
     * layout requirement; only one of them is readable in a bucket listing.
     * Open question for Day 22, flagged rather than silently resolved here -
     * changing the key format after passes exist would orphan every stored
     * object.
     */
    public String objectPrefix() {
        return (campusCode == null ? "campus-" + campusId : campusCode) + "/passes";
    }
}
