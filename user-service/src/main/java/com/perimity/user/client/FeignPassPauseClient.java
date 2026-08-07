package com.perimity.user.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The pause call, over Feign.
 *
 * Same interface as the RestClient and no-op versions, chosen by
 * PassPauseClientConfig. The caller - StudentProfileService and friends - never
 * learns which is in use.
 *
 * Returns false rather than throwing when the peer is unreachable. The profile
 * edit itself has already been saved and is correct; only the notification
 * failed. Throwing here would roll back a legitimate edit because a different
 * service was restarting.
 *
 * That does leave a real gap: a sensitive field changed and the pass stayed
 * active. It is logged at ERROR for exactly that reason, and Day 20 should add
 * a reconciliation sweep rather than pretending the call always succeeds.
 */
public class FeignPassPauseClient implements PassPauseClient {

    private static final Logger log = LoggerFactory.getLogger(FeignPassPauseClient.class);

    private final GatepassFeignClient gatepass;

    public FeignPassPauseClient(GatepassFeignClient gatepass) {
        this.gatepass = gatepass;
        log.info("FeignPassPauseClient active - sensitive-edit pauses go over Feign.");
    }

    @Override
    public boolean pauseAllForHolder(Long holderUserId, String reason, Long changedBy) {
        if (holderUserId == null) {
            return false;
        }
        try {
            var response = gatepass.pauseHolder(holderUserId,
                    new GatepassFeignClient.PauseRequest(reason, changedBy));
            return response != null && response.success();

        } catch (RuntimeException ex) {
            log.error("Could not pause passes for holder {} after a sensitive edit - "
                            + "their pass may still be ACTIVE. {}",
                    holderUserId, ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean resumeAllForHolder(Long holderUserId, String reason, Long changedBy) {
        if (holderUserId == null) {
            return false;
        }
        try {
            var response = gatepass.resumeHolder(holderUserId,
                    new GatepassFeignClient.PauseRequest(reason, changedBy));
            return response != null && response.success();

        } catch (RuntimeException ex) {
            log.error("Could not resume passes for holder {} after approval - "
                            + "their pass is still PAUSED and needs resuming by hand. {}",
                    holderUserId, ex.getMessage());
            return false;
        }
    }
}
