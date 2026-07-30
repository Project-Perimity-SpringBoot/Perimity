package com.perimity.guard.client;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT ONLY.
 *
 * Returns empty, so a DAILY scan logs as ordinary campus entry. Behavior 2 is
 * fully wired in ScanService - this is the one call it needs, and
 * HttpRunningEventClient turns the behaviour on with no other change.
 *
 * Selected by perimity.guard.clients=stub. See StubPassVerificationClient for
 * why this is a property rather than @ConditionalOnMissingBean.
 */
@Component
@ConditionalOnProperty(name = "perimity.guard.clients", havingValue = "stub")
public class StubRunningEventClient implements RunningEventClient {

    @Override
    public Optional<Long> runningEventFor(Long holderUserId) {
        return Optional.empty();
    }
}
