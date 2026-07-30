package com.perimity.qr.dto;

/**
 * Why a scanned token was refused by qr-service.
 *
 * Returned as a stable string code in QrDecryptResponse.reason, never as free
 * text. Codes rather than sentences because guard-service may one day branch on
 * them, and a caller that has to match on "Token was re-issued" breaks the day
 * someone fixes a typo.
 *
 * ==========================================================================
 * WHAT IS NOT IN THIS ENUM, AND WHY
 * ==========================================================================
 * There is no EXPIRED and no NOT_YET_VALID. A token outside its date window is
 * still a genuine, live token, so tokenValid stays true and the dates are
 * returned for guard-service to judge.
 *
 * That is not an oversight, it is the difference between a guard reading
 * "invalid pass" and "pass expired" on a red screen. The first sends a visitor
 * away confused; the second tells them exactly what to fix. QrDecryptResponse
 * says the same thing in its own comment: whether a pass may enter today is
 * guard-service's decision, and gatepass-service is the service that knows a
 * pass expired.
 */
public enum DecryptFailureReason {

    /**
     * The string did not decrypt: wrong key, tampered bytes, truncated scan, or
     * simply not one of our tokens. GCM's authentication tag catches all four
     * as one failure, which is the property that makes a forged token an
     * exception rather than a plausible-looking payload.
     */
    TOKEN_UNREADABLE,

    /**
     * It decrypted, but no qr_records row carries its hash.
     *
     * This should be impossible and is logged at ERROR when it happens. It
     * means either the row was deleted out from under a live pass, or somebody
     * holds the AES key and minted a token themselves. Both are worth waking up
     * for; neither is a normal denial.
     */
    TOKEN_UNKNOWN,

    /**
     * It decrypted and the row exists, but the row was invalidated - the pass
     * was re-issued or revoked, and this is the older QR.
     *
     * The common real-world case: someone kept the first email and shows that
     * PDF instead of the replacement.
     */
    TOKEN_SUPERSEDED,

    /**
     * The payload and the stored row disagree about which pass or campus this
     * token belongs to.
     *
     * Cannot happen through any normal path - the row is written from the same
     * payload the token was built from. Refused and logged at ERROR because the
     * only explanations are a bug in generation or a forged token that happened
     * to collide, and neither should quietly open a gate.
     */
    TOKEN_MISMATCH
}
