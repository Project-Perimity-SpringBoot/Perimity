package com.perimity.guard.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The documented default, with no network call.
 *
 * Selected by perimity.guard.clients.campus=stub. Used in tests and when running
 * guard-service alone, so a scan never waits on a campus-service that is not
 * running.
 *
 * Note this is not a fake: AMBER genuinely is the specified behaviour for a
 * campus that has not set `repeat_entry_result` (FR-CFG-3). The HTTP client
 * returns the same answer when the key is absent. The only difference is whether
 * we bothered to ask.
 */
@Component
@ConditionalOnProperty(name = "perimity.guard.clients.campus", havingValue = "stub")
public class DefaultCampusConfigClient implements CampusConfigClient {

    @Override
    public RepeatEntryPolicy repeatEntryPolicy(Long campusId) {
        return RepeatEntryPolicy.DEFAULT;
    }
}
