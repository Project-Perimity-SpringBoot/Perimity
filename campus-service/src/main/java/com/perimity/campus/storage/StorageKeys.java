package com.perimity.campus.storage;

import java.util.Locale;
import java.util.UUID;

/**
 * Builds object keys. One place, so the layout stays consistent across six
 * services and matches the structure in the Database Design document.
 *
 *   campuses/{campusCode}/logo-{uuid}.png
 *   campuses/{campusCode}/assets/{uuid}-{filename}
 *   bulk/{campusCode}/{batchId}/errors.csv
 *   passes/{campusCode}/pass-{id}-qr.png
 *
 * TYPE FIRST, THEN CAMPUS. The Database Design document lays the bucket out as
 * campuses/, profiles/, passes/, bulk/ at the top level, and campusLogo above
 * already followed it - bulkErrorReport did not, which put one campus's files
 * in two unrelated places in the same bucket. Corrected on Day 10, before
 * anything had written a key in the old shape.
 *
 * The ordering is not cosmetic once this is real S3: a lifecycle rule that
 * expires bulk artefacts after 90 days is one prefix match on bulk/, and an
 * IAM policy scoped to passes/ is one line. Neither is expressible when the
 * campus code comes first.
 *
 * Three rules worth keeping:
 *
 * 1. The campus CODE, not the id, is the top-level prefix. A human debugging a
 *    bucket at 2am can see which institution a file belongs to without opening
 *    the database. This is also why the code can never be edited.
 *
 * 2. A DISPLAYED file carries a UUID. Overwriting logo.png means a cached copy
 *    in CloudFront keeps serving the old image, and there is no way to tell
 *    which version a stale page is showing. A new key each time makes
 *    replacement unambiguous.
 *
 * 3. A DERIVED file does not. An error report is a pure function of one batch,
 *    it is fetched once by the person who just uploaded, and a retry that
 *    regenerates it should replace it rather than leave an orphan nobody will
 *    ever delete. Deterministic key, on purpose.
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

    /**
     * Deterministic - no UUID. See rule 3 above: re-running validation for the
     * same batch must overwrite its report, not accumulate copies.
     */
    public static String bulkErrorReport(String campusCode, Long batchId) {
        return "bulk/" + safe(campusCode) + "/" + batchId + "/errors.csv";
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
