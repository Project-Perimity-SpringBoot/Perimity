package com.perimity.campus.storage;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Development storage: a folder on disk.
 *
 * Active by default, so the project runs with no AWS account and no network.
 *
 * The folder must be gitignored - `storage-dev/` is already in .gitignore.
 * Committing a teammate's test uploads would be a mess and, once real photos
 * exist, a genuine data problem.
 */
@Service
@ConditionalOnProperty(name = "perimity.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path root;

    public LocalFileStorageService(@Value("${perimity.storage.local-dir}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
        log.warn("Storage is LOCAL, writing to {}. Set perimity.storage.type=s3 for the real thing.",
                root);
    }

    @Override
    public StoredObject put(String key, InputStream content, long sizeBytes, String contentType) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredObject(key, contentType, Files.size(target));
        } catch (IOException e) {
            throw new StorageException("Could not store " + key, e);
        }
    }

    /**
     * There is no signing locally, so this returns a path the dev controller
     * can serve. The SHAPE of the call matches S3, which is the point - service
     * code does not change when the implementation does.
     */
    @Override
    public String presignedReadUrl(String key, Duration validFor) {
        return "/api/campus/storage/local/" + key;
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", key, e.getMessage());
        }
    }

    /**
     * Resolves a key inside the root and refuses anything that escapes it.
     *
     * Without this check a key of "../../etc/passwd" would write outside the
     * storage folder. The DTO patterns already reject that shape, but this is
     * the layer that actually touches the filesystem, so it checks again.
     */
    public Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new StorageException("Rejected key that escapes the storage root: " + key, null);
        }
        return target;
    }

    public Path rootDir() {
        return root;
    }
}
