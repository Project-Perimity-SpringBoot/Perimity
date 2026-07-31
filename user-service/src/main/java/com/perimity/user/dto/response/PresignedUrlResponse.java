package com.perimity.user.dto.response;

import java.time.LocalDateTime;

/**
 * A short-lived link to one stored object.
 *
 * expiresAt is returned so the frontend can tell the difference between "this
 * link is stale, ask for another" and "this file is gone". Without it, a photo
 * that stops loading twenty minutes into a session looks like a broken upload.
 *
 * The URL is never stored anywhere. It is generated per request, on purpose:
 * persisting one would recreate the permanent public link this whole mechanism
 * exists to avoid.
 */
public record PresignedUrlResponse(String url, LocalDateTime expiresAt, int validForMinutes) {

    public static PresignedUrlResponse of(String url, int validForMinutes) {
        return new PresignedUrlResponse(
                url, LocalDateTime.now().plusMinutes(validForMinutes), validForMinutes);
    }
}
