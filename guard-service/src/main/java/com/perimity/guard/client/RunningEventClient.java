package com.perimity.guard.client;

import java.util.Optional;

/**
 * Behavior 2 support.
 *
 * The holder scanned their DAILY QR. Do they have an event running today? If
 * so the entry is credited to that event anyway, and the guard sees one green
 * light without learning the difference.
 *
 * gatepass-service already exposes this:
 *     GET /api/gatepass/internal/passes/holder/{id}/running-event
 *
 * Day 8 replaces the stub with a real call to it.
 */
public interface RunningEventClient {

    Optional<Long> runningEventFor(Long holderUserId);
}
