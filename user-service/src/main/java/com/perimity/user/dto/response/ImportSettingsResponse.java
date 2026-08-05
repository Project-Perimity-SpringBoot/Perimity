package com.perimity.user.dto.response;

import com.perimity.user.entity.CampusImportSettings;
import java.time.LocalDateTime;

/**
 * The campus intake form, as the settings screen sees it.
 *
 * ==========================================================================
 * THE SHEET ID IS RETURNED, THE FORM LINK IS RETURNED, AND THAT IS ALL
 * ==========================================================================
 * Neither is a secret - the form link is meant to be shared with students, and
 * the sheet id is useless without Drive permission on the document itself.
 *
 * What is NOT here is anything about the service account: its address, whether
 * the key loaded, which project it belongs to. `driveAvailable` is a single
 * boolean because a faculty member needs to know whether Pull will work, and
 * nothing more. The details belong in a server log, not in a response a
 * browser can read.
 */
public record ImportSettingsResponse(
        Long campusId,
        String formUrl,
        String responsesSheetId,

        /** Both halves present, so Pull and Download can be offered. */
        boolean configured,

        /**
         * Whether this server can actually reach Drive - the switch is on AND
         * the credentials loaded.
         *
         * Separate from `configured` on purpose. A campus can have its form set
         * up perfectly while the deployment has no Drive access at all, and the
         * two need different advice: one is "finish setting up", the other is
         * "download the sheet and upload it instead". Collapsing them into one
         * flag would send people to fix the wrong thing.
         */
        boolean driveAvailable,

        Long updatedBy,
        LocalDateTime updatedAt
) {

    public static ImportSettingsResponse from(CampusImportSettings e, boolean driveAvailable) {
        return new ImportSettingsResponse(
                e.getCampusId(),
                e.getFormUrl(),
                e.getResponsesSheetId(),
                e.isComplete(),
                driveAvailable,
                e.getUpdatedBy(),
                e.getUpdatedAt());
    }
}
