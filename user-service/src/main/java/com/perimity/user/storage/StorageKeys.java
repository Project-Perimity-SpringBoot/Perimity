package com.perimity.user.storage;

import java.util.Locale;
import java.util.UUID;

/**
 * Builds object keys for user-service. One place, so the layout stays
 * consistent and no caller ever hand-rolls a path.
 *
 *   profiles/campus-{campusId}/students/{userId}/photo-{uuid}.jpg
 *   profiles/campus-{campusId}/faculty/{userId}/photo-{uuid}.png
 *   profiles/campus-{campusId}/users/{userId}/documents/{uuid}-{filename}
 *
 * =====================================================================
 *  KEYS ARE GENERATED HERE, NEVER ACCEPTED FROM A CLIENT (SRS v1.1)
 * =====================================================================
 *
 * Before Day 9 the upload endpoint took an s3Key in the request body. That let
 * a caller name any path they liked - including one pointing at somebody else's
 * folder, so a student could register a document that reads as another
 * student's ID proof. The DTO that carried that field is gone.
 *
 * WHY campusId AND NOT THE CAMPUS CODE
 * campus-service prefixes its keys with the campus CODE, which reads better in
 * a bucket. user-service cannot: the code lives in campusdb and this service is
 * not allowed to read it, so every upload would need a cross-service call.
 * That would make a photo upload fail whenever campus-service restarts, and
 * worse, a key would then depend on a value fetched at write time - if the call
 * ever returned something different, the old objects would be unreachable.
 * campusId comes from the profile row that is already in hand.
 *
 * WHY EVERY KEY CARRIES A UUID
 * Overwriting photo.jpg means a cached copy in CloudFront keeps serving the old
 * image and there is no way to tell which version a stale page is showing. On a
 * gate pass that is not cosmetic - it is the wrong face next to the right name.
 * A new key each time makes replacement unambiguous.
 */
public final class StorageKeys {

    private StorageKeys() {
    }

    public static String studentPhoto(Long campusId, Long userId, String originalFilename) {
        return prefix(campusId) + "/students/" + userId + "/photo-" + UUID.randomUUID()
                + extensionOf(originalFilename);
    }

    public static String facultyPhoto(Long campusId, Long userId, String originalFilename) {
        return prefix(campusId) + "/faculty/" + userId + "/photo-" + UUID.randomUUID()
                + extensionOf(originalFilename);
    }

    public static String document(Long campusId, Long userId, String originalFilename) {
        return prefix(campusId) + "/users/" + userId + "/documents/" + UUID.randomUUID()
                + "-" + safe(originalFilename);
    }

    private static String prefix(Long campusId) {
        return "profiles/campus-" + (campusId == null ? "unknown" : campusId);
    }

    /**
     * Lowercase, and nothing that could escape the prefix or confuse a URL.
     *
     * The result must also satisfy ValidationPatterns.OBJECT_KEY, which is what
     * the entity checks before the row is written - so a key this method could
     * not produce is a key the database will refuse.
     */
    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "file";
        }
        String cleaned = value.toLowerCase(Locale.ROOT)
                // Anything outside the safe set becomes a hyphen. Note this
                // turns "/" into "-", so a path in a filename stops being a path.
                .replaceAll("[^a-z0-9._-]", "-")
                // Collapse runs of dots. Without this line "../../escape" keeps
                // its dots and produces a key containing "..", which
                // ValidationPatterns.OBJECT_KEY then refuses - and it refuses it
                // on the ENTITY, after the bytes are already in storage. The
                // upload would 500 with the file orphaned in the bucket.
                .replaceAll("\\.{2,}", ".")
                .replaceAll("-{2,}", "-")
                // A leading dot or hyphen is legal but reads as a hidden file
                // and makes a bucket listing confusing.
                .replaceAll("^[.-]+", "");

        if (cleaned.isBlank()) {
            return "file";
        }
        // A name of 300 characters is legal on some filesystems and would push
        // the key past the 512-character column.
        return cleaned.length() > 80 ? cleaned.substring(cleaned.length() - 80) : cleaned;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("^[a-z0-9]{1,5}$") ? "." + ext : "";
    }
}
