package com.perimity.auth.messaging;

import com.perimity.auth.entity.User;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.messaging.contract.UserCreatedEvent;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Announces a new account so user-service can provision its profile.
 *
 * ==========================================================================
 * PUBLISHED AFTER THE TRANSACTION COMMITS
 * ==========================================================================
 * Publishing inside the transaction is a real race, not a theoretical one:
 * RabbitMQ delivers in milliseconds, user-service would try to provision a
 * profile for account 21 before the INSERT has committed, and if the
 * transaction then rolled back there would be a profile for an account that
 * never existed.
 *
 * afterCommit fires only on a successful commit, so the account provably exists
 * before anyone is told about it. Same reasoning and the same mechanism as
 * gatepass-service's QrJobPublisher.
 *
 * ==========================================================================
 * A BROKER FAILURE MUST NOT FAIL ACCOUNT CREATION
 * ==========================================================================
 * The account is already saved and correct by the time this runs. Throwing here
 * would turn a RabbitMQ hiccup into "could not create user", which is both
 * wrong and confusing - the user WAS created.
 *
 * So a publish failure is logged loudly and swallowed. The cost is an account
 * with no profile, which is the exact state this whole mechanism exists to
 * prevent - but it is now a logged event with a named account rather than
 * silence, and two other safety nets cover it: the student's own details form
 * creates the row on save, and backfill-profiles.sh finds anything left over.
 *
 * Three layers, deliberately. This one is the fast path, not the guarantee.
 */
@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    /**
     * The only roles that have a profile in user-service.
     *
     * GUARD, CAMPUS_ADMIN and SUPER_ADMIN deliberately have none - there is no
     * entity for them, and ProfileType is STUDENT or FACULTY. VISITOR has none
     * either: a visitor is identified by the pass they were issued, not by a
     * campus profile, and bulk visitor upload would otherwise publish hundreds
     * of events that every consumer ignores.
     *
     * Filtering HERE rather than in the consumer keeps the queue meaningful. A
     * queue where most messages are no-ops makes its own depth useless as a
     * signal.
     */
    private static final Set<Role> ROLES_WITH_PROFILES = EnumSet.of(Role.STUDENT, Role.FACULTY);

    private final RabbitTemplate rabbit;

    public UserEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    /** Call from inside the creating transaction. The send happens after commit. */
    public void publishCreatedAfterCommit(User user) {
        if (user == null || !ROLES_WITH_PROFILES.contains(user.getRole())) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publishNow(user);
                        }
                    });
        } else {
            publishNow(user);
        }
    }

    private void publishNow(User user) {
        UserCreatedEvent event = new UserCreatedEvent(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getCampusId());
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_USER_CREATED, event);
            log.info("Published user.created for account {} ({})", user.getId(), user.getRole());

        } catch (RuntimeException ex) {
            // Deliberately not rethrown - see the class note. The account stands.
            log.error("Could not publish user.created for account {} ({}). "
                            + "That account may have no profile until the details form "
                            + "or backfill-profiles.sh creates one: {}",
                    user.getId(), user.getEmail(), ex.getMessage());
        }
    }
}
