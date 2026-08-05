package com.perimity.user.messaging;

import com.perimity.user.messaging.contract.UserCreatedEvent;
import com.perimity.user.service.ProfileProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Provisions a profile when auth-service announces a new account.
 *
 * ==========================================================================
 * THIS IS THE GUARANTEE, NOT A CONVENIENCE
 * ==========================================================================
 * Before this listener existed, the profile was created by a second API call
 * from the browser after the account came back. Any account created any other
 * way - or whose second call never happened - could sign in with no profile,
 * and no screen anywhere could create the missing row. That is why several
 * students see "No student profile exists for account 21" and why zero faculty
 * profiles exist, which in turn makes the visitor host dropdown empty and the
 * entire visitor journey unreachable.
 *
 * A queue turns that silent, permanent failure into a retried, visible one.
 *
 * ==========================================================================
 * THE THREE LAYERS, AND WHY ONE IS NOT ENOUGH
 * ==========================================================================
 *   1. This listener      - the guarantee for every account created from now on
 *   2. PUT /students/me/details creates the row if missing - covers a student
 *                            whose event was lost while the broker was down
 *   3. backfill-profiles.sh - finds anything that predates all of this
 *
 * Layer 1 alone would be enough if brokers never failed and no data predated
 * the change. Neither is true.
 *
 * ==========================================================================
 * FAILURE HANDLING
 * ==========================================================================
 * Throwing sends the message back for retry - three attempts with backoff, then
 * the DLQ. That is the right response to a database blip.
 *
 * NOT throwing acknowledges the message and it is gone forever. So the only
 * things swallowed here are conditions retrying cannot fix: an unknown role, a
 * missing id. Everything else is allowed to propagate, because a message
 * sitting in user.created.dlq is a problem somebody can find, and an
 * acknowledged failure is one nobody ever will.
 */
@Component
public class UserCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(UserCreatedListener.class);

    private final ProfileProvisioningService provisioning;

    public UserCreatedListener(ProfileProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_USER_CREATED)
    public void onUserCreated(UserCreatedEvent event) {
        if (event == null) {
            log.error("Empty user.created message - ignoring.");
            return;
        }

        log.debug("user.created received for account {} ({})", event.userId(), event.role());

        // Deliberately unguarded. A DataAccessException here must reach the
        // container so the message retries and then dead-letters, rather than
        // being logged and lost - see the class note.
        provisioning.provision(event.userId(), event.role(), event.campusId());
    }
}
