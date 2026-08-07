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

    @Override
    public boolean resumeAllForHolder(Long holderUserId, String reason, Long changedBy) {
        // WARN for the same reason as pause, and it matters slightly more here:
        // a missed pause leaves a pass working, which a person notices. A missed
        // resume leaves it dead, and the student is told to wait for staff who
        // have already done their part.
        log.warn("gatepass-service is not configured - NOT resuming passes for holder {} after: {}. "
                        + "Their pass stays PAUSED until it is resumed by hand.",
                holderUserId, reason);
        return false;
    }
}
