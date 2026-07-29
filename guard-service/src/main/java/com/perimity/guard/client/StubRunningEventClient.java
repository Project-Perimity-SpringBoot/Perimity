package com.perimity.guard.client;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT ONLY. Delete on Day 8.
 *
 * Returns empty, so a DAILY scan logs as ordinary campus entry. Behavior 2 is
 * fully wired in ScanService - this is the one call it needs, and swapping in
 * the real gatepass-service client turns the behaviour on with no other change.
 */
@Component
@ConditionalOnMissingBean(ignored = StubRunningEventClient.class, value = RunningEventClient.class)
public class StubRunningEventClient implements RunningEventClient {

    @Override
    public Optional<Long> runningEventFor(Long holderUserId) {
        return Optional.empty();
    }
}
