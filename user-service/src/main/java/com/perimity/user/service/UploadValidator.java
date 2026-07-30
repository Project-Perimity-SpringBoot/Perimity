package com.perimity.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Decides whether an uploaded file is what it says it is.
 *
 * =========================================================================
 *  THE CONTENT TYPE A BROWSER SENDS IS A CLAIM, NOT A FACT (SRS v1.1)
 * =========================================================================
 *
 * Renaming payload.exe to photo.png takes one second and the browser will
 * cheerfully report image/png. Before Day 9 this service only checked the
 * declared type, and the code said so - it was never a security control.
 * These leading bytes are what the file actually is.
 *
 * That matters more here than for a campus logo. This bucket holds identity
 * documents, and a stored file that is really HTML, served back from our own
 * origin, is stored cross-site scripting against every admin who opens it.
 *
 * Both checks are kept, in cheapest-first order: the declared type is one
 * string comparison and rejects most honest mistakes without reading a byte.
 */
@Component
public class UploadValidator {

    /**
     * Photos: real raster images only.
     *
     * SVG is deliberately absent. An SVG is XML and can carry script, so
     * serving one from our own domain is a stored XSS waiting to happen.
     * A profile photo has no business being a vector anyway.
     */
    private static final List<String> PHOTO_TYPES =
            List.of("image/png", "image/jpeg", "image/webp");

    /** Documents: images plus PDF, matching the limits in the service README. */
    private static final List<String> DOCUMENT_TYPES =
            List.of("application/pdf", "image/png", "image/jpeg");

    // File signatures. WebP is a RIFF container, so its first four bytes are
    // "RIFF" and bytes 8..11 are "WEBP" - both are checked.
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};

    /** @return the verified content type, lower-cased, safe to persist */
    public String validatePhoto(MultipartFile file, long maxBytes) {
        String type = common(file, maxBytes, PHOTO_TYPES, "photo");
        if (!looksLikeImage(file)) {
            throw new IllegalArgumentException(
                    "That file is not the image it claims to be. Upload a real PNG, JPEG or WebP.");
        }
        return type;
    }

    public String validateDocument(MultipartFile file, long maxBytes) {
        String type = common(file, maxBytes, DOCUMENT_TYPES, "document");
        if (!(looksLikeImage(file) || startsWith(head(file), PDF))) {
            throw new IllegalArgumentException(
                    "That file is not the type it claims to be. Upload a real PDF, PNG or JPEG.");
        }
        return type;
    }

    private String common(MultipartFile file, long maxBytes, List<String> allowed, String what) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        // Spring's multipart limit rejects anything oversized before this runs,
        // but that produces an error nobody can act on, so size is checked again
        // here to give a message with a number in it.
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "The " + what + " must be smaller than " + (maxBytes / 1024 / 1024) + " MB.");
        }
        String declared = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT).trim();

        if (!allowed.contains(declared)) {
            throw new IllegalArgumentException(
                    "A " + what + " must be one of " + String.join(", ", allowed)
                            + ". This was sent as " + (declared.isEmpty() ? "nothing" : declared) + ".");
        }
        return declared;
    }

    private boolean looksLikeImage(MultipartFile file) {
        byte[] head = head(file);
        if (startsWith(head, PNG) || startsWith(head, JPEG)) {
            return true;
        }
        return startsWith(head, RIFF) && head.length >= 12
                && Arrays.equals(Arrays.copyOfRange(head, 8, 12), WEBP);
    }

    /** Reads only the first bytes. The rest of the file is never held in memory here. */
    private byte[] head(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(12);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }
}
