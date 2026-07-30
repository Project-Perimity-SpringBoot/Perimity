package com.perimity.gatepass.storage;

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

    /**
     * Reads an object back. NOT in Arham's campus-service original - added here
     * because the bulk engine needs it and campus-service did not.
     *
     * The reason it exists: the two-phase upload validates the sheet, then waits
     * for a human to click Confirm. On Confirm the sheet has to be read a second
     * time. The alternative was persisting every parsed row to a new table
     * between the two clicks, which is a whole entity, repository and migration
     * to hold data that is already sitting in storage, immutable, under a key we
     * saved on the batch row.
     *
     * Re-reading also has a correctness benefit: the second pass re-runs the
     * blocklist check, so anyone barred between Validate and Confirm is caught.
     * Persisted rows would have frozen the stale answer.
     *
     * The caller must close the stream.
     */
    InputStream openStream(String key);

    boolean exists(String key);

    /**
     * Deletes an object. Used when a logo is replaced, so the old file does not
     * sit in the bucket forever costing money and holding data nobody expects
     * to still exist.
     */
    void delete(String key);
}
