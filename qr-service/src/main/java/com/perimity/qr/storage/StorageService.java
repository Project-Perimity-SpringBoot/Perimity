package com.perimity.qr.storage;

/**
 * Where QR PNGs and pass PDFs are written.
 *
 * An interface with one local implementation today, deliberately. Day 22
 * moves storage to S3 behind CloudFront, and the whole of that change should
 * be one new class implementing this interface plus a property switch -
 * nothing in QrRecordService, the queue consumer or the controllers should
 * know which one is in use.
 *
 * It is also the only honest option right now: docker-compose runs postgres,
 * redis, rabbitmq and mongo. There is no MinIO container, so an S3 client
 * would have nothing to talk to and every generation would fail locally.
 *
 * Keys, never bytes, are what reach the database. A key must satisfy
 * ValidationPatterns.OBJECT_KEY, which rejects ".." so a key can never walk
 * out of the storage root.
 */
public interface StorageService {

    /**
     * Writes bytes under the given key, replacing anything already there.
     *
     * Overwrite rather than fail-on-exists because a retried generation job
     * must be able to finish. The key embeds the QrRecord id, so a retry
     * writes the same object rather than orphaning the first attempt.
     *
     * @return the key, so callers can persist it in one expression
     */
    String put(String key, byte[] content, String contentType);

    /** Reads an object back. Used by the Day 6 download endpoint. */
    byte[] get(String key);

    boolean exists(String key);
}
