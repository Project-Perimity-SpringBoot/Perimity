package com.perimity.campus.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * Object storage, behind an interface.
 *
 * ===================================================================
 *  THIS IS THE REFERENCE IMPLEMENTATION. The other five services copy it.
 * ===================================================================
 *
 * Two implementations, chosen by one property:
 *
 *   perimity.storage.type=local   a folder on disk. The DEFAULT.
 *   perimity.storage.type=s3      the real thing.
 *
 * Local is the default on purpose. Nobody should need an AWS account, a
 * credential file or a network connection to run this project, and a teammate
 * who cannot start the service is a teammate who cannot work. On Day 22 one
 * property flips and nothing else changes.
 *
 * It also keeps the demo safe: if AWS is unreachable on presentation day, the
 * whole system still runs from a laptop.
 */
public interface StorageService {

    /**
     * @param key         full object key, e.g. campuses/north-campus/logo.png
     * @param contentType the REAL type, verified by the caller - never the one
     *                    the browser claimed
     */
    StoredObject put(String key, InputStream content, long sizeBytes, String contentType);

    /** Short-lived read URL. Never hand out a permanent public link. */
    String presignedReadUrl(String key, Duration validFor);

    boolean exists(String key);

    /**
     * Deletes an object. Used when a logo is replaced, so the old file does not
     * sit in the bucket forever costing money and holding data nobody expects
     * to still exist.
     */
    void delete(String key);
}
