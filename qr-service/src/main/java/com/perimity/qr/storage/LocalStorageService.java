package com.perimity.qr.storage;

import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Writes objects to a directory on disk.
 *
 * @ConditionalOnProperty with matchIfMissing = true: this is the default, and
 * on Day 22 an S3 implementation annotated with the opposite condition takes
 * over by setting one property. Neither class needs to know about the other.
 *
 * The path traversal guard below is not redundant with the OBJECT_KEY regex.
 * The regex validates keys arriving from outside; this guards the filesystem
 * itself, which is the thing that actually gets damaged if a key like
 * "..%2f..%2fetc" ever gets past validation. Two independent checks, because
 * writing outside the storage root is not a recoverable mistake.
 */
@Service
@ConditionalOnProperty(name = "qr.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(@Value("${qr.storage.local.root:./storage}") String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create storage root at " + root, ex);
        }
    }

    @Override
    public String put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            /*
             * Write to a temp file in the same directory, then move. A move
             * within one filesystem is atomic, so a reader can never observe a
             * half-written PNG - which at a gate would be a QR that scans as
             * garbage rather than one that is simply not there yet.
             */
            Path temp = Files.createTempFile(target.getParent(), ".tmp-", null);
            Files.write(temp, content);
            Files.move(temp, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return key;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write object " + key, ex);
        }
    }

    @Override
    public byte[] get(String key) {
        Path target = resolve(key);
        if (!Files.exists(target)) {
            throw new EntityNotFoundException("No stored object for key " + key);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read object " + key, ex);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    /**
     * Turns a key into a path, refusing anything that escapes the root.
     *
     * normalize() collapses ".." segments, so comparing the result against the
     * root catches traversal regardless of how it was encoded.
     */
    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the storage root: " + key);
        }
        return resolved;
    }
}
