package com.perimity.guard.client;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No profile lookup, no network call. Selected by
 * perimity.guard.clients.profile=stub.
 *
 * Returning empty is not a fake result: a visitor genuinely has no profile in
 * user-service, so "no photo" is a real and common answer at a gate. The scan
 * path has to render that case correctly either way.
 */
@Component
@ConditionalOnProperty(name = "perimity.guard.clients.profile", havingValue = "stub")
public class NoProfileClient implements HolderProfileClient {

    @Override
    public Optional<HolderProfile> profileFor(Long userId) {
        return Optional.empty();
    }
}
