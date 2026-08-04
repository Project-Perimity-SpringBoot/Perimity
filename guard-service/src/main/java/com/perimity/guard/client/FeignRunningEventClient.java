package com.perimity.guard.client;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Behavior 2 lookup, over Feign.
 *
 * The THIRD implementation of RunningEventClient, alongside the RestClient one
 * and the stub. All three sit behind the same interface, and one property picks
 * the winner - so switching to Feign is a config change and rolling back is the
 * same change in reverse. ScanService never learns which is in use.
 *
 * The try/catch is not optional. Feign throws on any non-2xx or connection
 * failure, and losing event attribution must never be the reason a gate stops
 * working. Same rule the RestClient version already followed.
 */
@Component
@ConditionalOnProperty(name = "perimity.clients.mode", havingValue = "feign")
// AND the stub must not have been asked for.
//
// Without this second condition, perimity.clients.mode=feign together with
// perimity.guard.clients.events=stub registers BOTH this class and
// StubRunningEventClient, and the context dies on NoUniqueBeanDefinitionException.
// That pair is not hypothetical: it is what a developer working offline gets the
// moment feign becomes the default.
//
// An explicit request for the stub wins over the transport choice, because
// "stub" answers a different question - it says "do not call anybody" - and
// which HTTP library we would have used is then irrelevant.
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${perimity.guard.clients.events:http}' != 'stub'")
public class FeignRunningEventClient implements RunningEventClient {

    private static final Logger log = LoggerFactory.getLogger(FeignRunningEventClient.class);

    private final GatepassFeignClient gatepass;

    public FeignRunningEventClient(GatepassFeignClient gatepass) {
        this.gatepass = gatepass;
        log.info("FeignRunningEventClient active - Behavior 2 attribution over Feign.");
    }

    @Override
    public Optional<Long> runningEventFor(Long holderUserId) {
        if (holderUserId == null) {
            return Optional.empty();
        }
        try {
            var response = gatepass.runningEvent(holderUserId);
            if (response == null || response.data() == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.data().eventId());

        } catch (RuntimeException ex) {
            log.warn("Behavior 2 lookup failed for holder {} - logging as normal campus entry. {}",
                    holderUserId, ex.getMessage());
            return Optional.empty();
        }
    }
}
