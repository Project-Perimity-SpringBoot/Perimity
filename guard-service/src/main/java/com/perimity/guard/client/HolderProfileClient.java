package com.perimity.guard.client;

import java.util.Optional;

/**
 * What user-service can tell the gate about the person holding the pass.
 *
 * FR-SCAN-9 requires the guard to see the holder's photo alongside the colour,
 * so they can look up and check the face against the pass. That check is the
 * whole mitigation for the one attack the QR design deliberately does not
 * prevent: a screenshot of somebody else's valid pass.
 *
 * Narrow on purpose. guard-service has no business reading a roll number, an
 * address or a national id, and an interface that cannot express those cannot
 * accidentally start returning them.
 */
public interface HolderProfileClient {

    /**
     * Empty when there is no profile, or user-service cannot be reached.
     *
     * Never throws. A missing photo must never stop someone entering - the
     * colour and the name are already on screen, and a guard with a name is
     * better off than a guard with an error message.
     */
    Optional<HolderProfile> profileFor(Long userId);

    /**
     * photoKey is an object-storage key; photoUrl is a short-lived signed link
     * to the same object, minted fresh by user-service on every read.
     *
     * Both are carried because they are different things. The key is stable and
     * meaningless to a browser; the URL is renderable and expires. The gate
     * needs the second one — a guard has to look at a face, which was the whole
     * point of FR-SCAN-9 — and it was being dropped here.
     *
     * Either may be null and that is normal: a visitor has no profile at all.
     */
    record HolderProfile(Long userId, String identifierCode, String photoKey, String photoUrl) { }
}
