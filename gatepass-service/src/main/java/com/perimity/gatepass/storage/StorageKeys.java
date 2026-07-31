package com.perimity.gatepass.storage;

import java.util.Locale;
import java.util.UUID;

/**
 * Builds object keys for gatepass-service.
 *
 * Deliberately the same shape as campus-service's StorageKeys (Arham), so a
 * bucket listing reads consistently across services. His version already
 * defines bulkErrorReport with exactly this layout - it was written for this
 * engine before this engine existed. The two must not drift.
 *
 *   {campusCode}/bulk/{batchId}/sheet-{uuid}.xlsx
 *   {campusCode}/bulk/{batchId}/errors.csv
 *
 * Two rules carried over unchanged:
 *
 * 1. The campus CODE, not the id, is the top-level prefix. Someone debugging a
 *    bucket at 2am can see which institution a file belongs to without opening
 *    a database. This is also why campus code can never be edited.
 *
 * 2. The uploaded sheet carries a UUID. Two faculty uploading attendees.xlsx
 *    for the same batch must not overwrite each other, and a cached copy of a
 *    replaced file must not keep being served.
 *
 * errors.csv does NOT carry a UUID, on purpose: there is exactly one error
 * report per batch and it is rewritten if the batch is re-validated. A stable
 * key means the download link on the summary screen never goes stale.
 */
public final class StorageKeys {

    private StorageKeys() { }

    /** Where the uploaded spreadsheet itself lands. */
    public static String bulkSheet(String campusCode, Long batchId, String originalFilename) {
        return safe(campusCode) + "/bulk/" + batchId + "/sheet-" + UUID.randomUUID()
                + extensionOf(originalFilename);
    }

    /**
     * The downloadable "row 34: invalid email" report.
     *
     * Identical to campus-service's StorageKeys.bulkErrorReport. If you change
     * one, change both.
     */
    public static String bulkErrorReport(String campusCode, Long batchId) {
        return safe(campusCode) + "/bulk/" + batchId + "/errors.csv";
    }

    /**
     * The batch id is not known until the row is inserted, but the sheet has to
     * be stored before validation can read it. This is the holding key used for
     * that gap - the file is moved to its real key once the id exists.
     *
     * Kept out of the {batchId} tree so an orphan from a failed insert is
     * obvious rather than looking like a real batch.
     */
    public static String bulkStaging(String campusCode, String originalFilename) {
        return safe(campusCode) + "/bulk/_staging/" + UUID.randomUUID()
                + extensionOf(originalFilename);
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
