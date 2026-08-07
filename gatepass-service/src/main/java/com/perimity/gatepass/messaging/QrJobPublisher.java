package com.perimity.gatepass.messaging;

import com.perimity.gatepass.client.InternalServiceClient;
import com.perimity.gatepass.entity.Event;
import com.perimity.gatepass.entity.GatePass;
import com.perimity.gatepass.entity.enums.PassType;
import com.perimity.gatepass.messaging.contract.QrGenerationJob;
import com.perimity.gatepass.repository.EventRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Builds and publishes the QR generation job.
 *
 * ==========================================================================
 *  THE IMPORTANT BIT: the message is published AFTER the transaction commits.
 * ==========================================================================
 *
 * Publish inside the transaction and there is a real race: RabbitMQ delivers
 * in milliseconds, qr-service reads pass 118 before the INSERT has committed,
 * finds nothing, and fails. Worse, if the transaction then rolls back, a job
 * exists for a pass that does not.
 *
 * afterCommit fires only on a successful commit, so the pass provably exists
 * before anyone is told about it. This is the classic dual-write problem, and
 * afterCommit is the cheap correct answer at this scale. A full transactional
 * outbox would be the answer at a scale we do not have.
 */
@Component
public class QrJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(QrJobPublisher.class);

    private final RabbitTemplate rabbit;
    private final EventRepository eventRepository;
    private final InternalServiceClient internal;

    public QrJobPublisher(RabbitTemplate rabbit, EventRepository eventRepository,
                          InternalServiceClient internal) {
        this.rabbit = rabbit;
        this.eventRepository = eventRepository;
        this.internal = internal;
    }

    /** Call this from inside a transaction. The send happens after it commits. */
    public void publishAfterCommit(GatePass pass) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publishNow(pass);
                        }
                    });
        } else {
            publishNow(pass);
        }
    }

    public void publishNow(GatePass pass) {
        try {
            QrGenerationJob job = buildJob(pass);
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_GENERATE, job);
            log.info("QR job {} published for pass {}", job.jobId(), pass.getId());

        } catch (RuntimeException ex) {
            // The pass is already saved and correct; only the notification
            // failed. Do NOT rethrow - that would turn a broker hiccup into a
            // failed pass issue. The pass sits at PENDING and can be republished.
            log.error("Could not publish QR job for pass {} - it will stay PENDING: {}",
                    pass.getId(), ex.getMessage());
        }
    }

    /**
     * Assembles everything qr-service needs in one message.
     *
     * The enrichment calls all fail soft. A missing campus name means a slightly
     * plainer PDF, not a pass that never gets issued.
     */
    private QrGenerationJob buildJob(GatePass pass) {
        String campusName = null;
        String campusCode = null;
        var campus = internal.campusOf(pass.getCampusId());
        if (campus.isPresent()) {
            campusName = campus.get().name();
            campusCode = campus.get().code();
        }

        String holderEmail = internal.emailOf(pass.getHolderUserId()).orElse(null);
        if (holderEmail == null) {
            log.warn("No email found for holder {} - pass {} will be generated but not emailed",
                    pass.getHolderUserId(), pass.getId());
        }

        String eventName = null;
        if (pass.getEventId() != null) {
            eventName = eventRepository.findById(pass.getEventId())
                    .map(Event::getName).orElse(null);
        }

        /*
         * The department, for the PDF.
         *
         * Fails soft like every other enrichment here: a visitor has no profile
         * at all and this correctly returns empty, and user-service being down
         * costs a line on a PDF rather than a pass that never issues.
         */
        String departmentName = internal.profileOf(pass.getHolderUserId())
                .map(InternalServiceClient.ProfileView::departmentName)
                .orElse(null);

        return new QrGenerationJob(
                UUID.randomUUID().toString(),
                pass.getId(),
                pass.getCampusId(),
                campusName,
                campusCode,
                pass.getHolderUserId(),
                pass.getHolderName(),
                holderEmail,
                departmentName,
                pass.getPassType().name(),
                pass.getEventId(),
                eventName,
                // Day 10. The pass carries its own batch, so the bulk engine
                // needs no separate publish path and this method needs no new
                // parameter. Null on a single approval, which is correct.
                pass.getBatchId(),
                pass.getValidFrom(),
                pass.getValidTo(),
                null,
                EmailCopy.subjectFor(pass.getPassType(), eventName, campusName),
                EmailCopy.greetingFor(pass, eventName),
                LocalDateTime.now());
    }

    /**
     * Email wording lives here, not in qr-service.
     *
     * gatepass-service knows whether this is a daily pass or an event pass and
     * what the event is called. qr-service knows how to render a PDF and talk
     * to SES. Keeping the words on this side means changing them never touches
     * the service that sends them.
     */
    static final class EmailCopy {

        private EmailCopy() { }

        static String subjectFor(PassType type, String eventName, String campusName) {
            String where = campusName == null ? "campus" : campusName;
            if (type == PassType.EVENT && eventName != null) {
                return "Your gate pass for " + eventName;
            }
            return "Your gate pass for " + where;
        }

        static String greetingFor(GatePass pass, String eventName) {
            String name = pass.getHolderName() == null ? "there" : pass.getHolderName();

            if (pass.getPassType() == PassType.EVENT && eventName != null) {
                return "Hi " + name + ",\n\n"
                        + "Welcome to " + eventName + " on "
                        + formatRange(pass) + ".\n"
                        + "Your entry QR pass is attached - show it at the gate.\n\n"
                        + "Use this QR for the programme.";
            }
            if (pass.getValidTo() == null) {
                return "Hi " + name + ",\n\n"
                        + "Your campus gate pass is attached. It has no end date - "
                        + "show it at any gate.";
            }
            return "Hi " + name + ",\n\n"
                    + "Your gate pass is attached, valid " + formatRange(pass) + ".\n"
                    + "Show it at the gate.";
        }

        private static String formatRange(GatePass pass) {
            if (pass.getValidTo() == null || pass.getValidTo().equals(pass.getValidFrom())) {
                return String.valueOf(pass.getValidFrom());
            }
            return pass.getValidFrom() + " to " + pass.getValidTo();
        }
    }
}
