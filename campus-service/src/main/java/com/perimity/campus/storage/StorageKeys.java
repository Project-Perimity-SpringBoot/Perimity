package com.perimity.campus.storage;

import java.util.Locale;
import java.util.UUID;

/**
 * Builds object keys. One place, so the layout stays consistent across six
 * services and matches the structure in the Database Design document.
 *
 *   campuses/{campusCode}/logo-{uuid}.png
 *   campuses/{campusCode}/assets/{uuid}-{filename}
 *   {campusCode}/bulk/{batchId}/errors.csv
 *   {campusCode}/passes/pass-{id}-qr.png
 *
 * Two rules worth keeping:
 *
 * 1. The campus CODE, not the id, is the top-level prefix. A human debugging a
 *    bucket at 2am can see which institution a file belongs to without opening
 *    the database. This is also why the code can never be edited.
 *
 * 2. Every key carries a UUID. Overwriting logo.png means a cached copy in
 *    CloudFront keeps serving the old image, and there is no way to tell which
 *    version a stale page is showing. A new key each time makes replacement
 *    unambiguous.
 */
public final class StorageKeys {

    private StorageKeys() { }

    public static String campusLogo(String campusCode, String originalFilename) {
        return "campuses/" + safe(campusCode) + "/logo-" + UUID.randomUUID()
                + extensionOf(originalFilename);
    }

    public static String campusAsset(String campusCode, String originalFilename) {
        return "campuses/" + safe(campusCode) + "/assets/" + UUID.randomUUID() + "-"
                + safe(originalFilename);
    }

    public static String bulkErrorReport(String campusCode, Long batchId) {
        return safe(campusCode) + "/bulk/" + batchId + "/errors.csv";
    }

    /** Lowercase, and nothing that could escape the prefix or confuse a URL. */
    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-")
                .replaceAll("-{2,}", "-");
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
