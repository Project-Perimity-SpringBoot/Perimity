package com.perimity.user.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when perimity.services.gatepass-url is blank - a deliberate act since
 * Day 12, when that property gained a working localhost default. Blank it to
 * run user-service on its own while working on profiles.
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
                        + "Set perimity.services.gatepass-url to enable this.",
                holderUserId, reason);
        return false;
    }
}
