package com.perimity.user.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when perimity.gatepass.base-url is not configured - the normal state
 * when someone runs user-service on its own to work on profiles.
 *
 * It logs at WARN rather than staying quiet, because a silent no-op here is a
 * pass that should have been held and was not. Anyone reading the log sees
 * exactly which holder was missed.
 */
public class NoOpPassPauseClient implements PassPauseClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpPassPauseClient.class);

    @Override
    public boolean pauseAllForHolder(Long holderUserId, String reason, Long changedBy) {
        log.warn("gatepass-service is not configured - NOT pausing passes for holder {} after: {}. "
                        + "Set perimity.gatepass.base-url to enable this.",
                holderUserId, reason);
        return false;
    }
}
