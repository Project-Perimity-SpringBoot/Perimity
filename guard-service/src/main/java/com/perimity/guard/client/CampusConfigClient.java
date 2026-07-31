package com.perimity.guard.client;

/**
 * Reads the one campus setting the gate cares about.
 *
 * Deliberately a single method, not a general config reader. guard-service has
 * no business knowing what else campus-service stores, and a narrow contract is
 * one that cannot drift.
 */
public interface CampusConfigClient {

    /**
     * Never throws, never returns null. A campus that has not set the key, and a
     * campus-service that cannot be reached, both yield the documented default -
     * because a scan must not fail over a display preference.
     */
    RepeatEntryPolicy repeatEntryPolicy(Long campusId);
}
