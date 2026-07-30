package com.perimity.campus.service;

import com.perimity.campus.dto.response.CampusResponse;
import com.perimity.campus.entity.Campus;
import com.perimity.campus.exception.ResourceNotFoundException;
import com.perimity.campus.repository.CampusRepository;
import com.perimity.campus.storage.StorageKeys;
import com.perimity.campus.storage.StorageService;
import com.perimity.campus.storage.StoredObject;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Campus logo upload.
 *
 * Files go to object storage; only the KEY goes in the database. That rule is
 * in the Database Design document and it is not negotiable - binary in a
 * relational column bloats backups, breaks replication and makes every query
 * that touches the row slower.
 */
@Service
public class CampusAssetService {

    private static final Logger log = LoggerFactory.getLogger(CampusAssetService.class);

    /**
     * Real image types only. Note SVG is NOT here, deliberately: an SVG is XML
     * and can carry script, so serving one from our own domain is a stored XSS
     * waiting to happen.
     */
    private static final List<String> ALLOWED_TYPES =
            List.of("image/png", "image/jpeg", "image/webp");

    /**
     * File signatures. The browser-supplied content type is a claim, not a
     * fact - renaming payload.exe to logo.png takes one second. These first
     * bytes are what the file actually is.
     */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};

    private final CampusRepository campusRepository;
    private final StorageService storage;
    private final long maxLogoBytes;
    private final int presignMinutes;

    public CampusAssetService(CampusRepository campusRepository,
                              StorageService storage,
                              @Value("${perimity.storage.max-logo-mb}") long maxLogoMb,
                              @Value("${perimity.storage.presign-minutes}") int presignMinutes) {
        this.campusRepository = campusRepository;
        this.storage = storage;
        this.maxLogoBytes = maxLogoMb * 1024 * 1024;
        this.presignMinutes = presignMinutes;
    }

    @Transactional
    public CampusResponse uploadLogo(Long campusId, MultipartFile file) {
        Campus campus = campusRepository.findById(campusId)
                .orElseThrow(() -> ResourceNotFoundException.of("Campus", campusId));

        validate(file);

        String key = StorageKeys.campusLogo(campus.getCode(), file.getOriginalFilename());
        String previousKey = campus.getLogoS3Key();

        try (InputStream in = file.getInputStream()) {
            StoredObject stored = storage.put(key, in, file.getSize(), file.getContentType());
            campus.setLogoS3Key(stored.key());
            campusRepository.save(campus);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded file", e);
        }

        // Only after the new one is safely saved. Deleting first would leave the
        // campus with no logo at all if the upload then failed.
        if (previousKey != null && !previousKey.equals(key)) {
            storage.delete(previousKey);
            log.info("Replaced logo for campus {}, removed {}", campus.getCode(), previousKey);
        }

        return CampusResponse.from(campus);
    }

    /**
     * A short-lived link, generated on demand.
     *
     * The bucket is private and stays private. A permanent public URL cannot be
     * un-shared once it leaks; this one stops working in minutes.
     */
    @Transactional(readOnly = true)
    public String logoUrl(Long campusId) {
        Campus campus = campusRepository.findById(campusId)
                .orElseThrow(() -> ResourceNotFoundException.of("Campus", campusId));

        if (campus.getLogoS3Key() == null) {
            throw new ResourceNotFoundException("Campus " + campusId + " has no logo");
        }
        return storage.presignedReadUrl(campus.getLogoS3Key(), Duration.ofMinutes(presignMinutes));
    }

    @Transactional
    public CampusResponse removeLogo(Long campusId) {
        Campus campus = campusRepository.findById(campusId)
                .orElseThrow(() -> ResourceNotFoundException.of("Campus", campusId));

        if (campus.getLogoS3Key() != null) {
            storage.delete(campus.getLogoS3Key());
            campus.setLogoS3Key(null);
            campusRepository.save(campus);
        }
        return CampusResponse.from(campus);
    }

    /**
     * Four checks, in cheapest-first order.
     *
     * Spring's multipart limit rejects anything oversized before this runs, but
     * that returns an unhelpful error, so size is checked again here to give a
     * message a human can act on.
     */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (file.getSize() > maxLogoBytes) {
            throw new IllegalArgumentException(
                    "The logo must be smaller than " + (maxLogoBytes / 1024 / 1024) + " MB.");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "The logo must be a PNG, JPEG or WebP image. SVG is not accepted.");
        }
        if (!looksLikeAnImage(file)) {
            throw new IllegalArgumentException(
                    "That file is not the image type it claims to be.");
        }
    }

    /** Reads the first bytes and compares them against known image signatures. */
    private boolean looksLikeAnImage(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (head.length < 4) {
                return false;
            }
            return startsWith(head, PNG) || startsWith(head, JPEG) || startsWith(head, RIFF);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }
}
