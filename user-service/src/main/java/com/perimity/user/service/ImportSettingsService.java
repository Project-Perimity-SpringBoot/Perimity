package com.perimity.user.service;

import com.perimity.user.entity.CampusImportSettings;
import com.perimity.user.repository.CampusImportSettingsRepository;
import com.perimity.user.security.CurrentUser;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The campus's intake form and the sheet it writes to.
 *
 * ==========================================================================
 * FACULTY PASTE URLs, NOT IDS
 * ==========================================================================
 * Nobody should have to know that the useful part of
 * https://docs.google.com/spreadsheets/d/1AbC.../edit#gid=0
 * is the bit between /d/ and the next slash.
 *
 * Asking for an id would produce exactly one support question forever, and the
 * wrong answer to it - a form id pasted where a sheet id belongs - fails later
 * with "could not export", which points at Drive rather than at the paste.
 *
 * So a whole URL is accepted and the id is taken out of it. A bare id is also
 * accepted, because somebody will paste one.
 */
@Service
public class ImportSettingsService {

    private static final Logger log = LoggerFactory.getLogger(ImportSettingsService.class);

    /**
     * The id out of a Docs, Sheets or Drive URL.
     *
     * Google has used several shapes over the years and will use more. The id
     * itself has been stable: a long run of URL-safe characters after /d/ or
     * after ?id=.
     */
    private static final Pattern DOC_ID = Pattern.compile(
            "(?:/d/|[?&]id=)([A-Za-z0-9_-]{16,})");

    /** A bare id somebody pasted on its own. */
    private static final Pattern BARE_ID = Pattern.compile("^[A-Za-z0-9_-]{16,}$");

    private final CampusImportSettingsRepository repository;
    private final CurrentUser currentUser;

    public ImportSettingsService(CampusImportSettingsRepository repository,
                                 CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    /**
     * This campus's settings, empty rather than absent when never configured.
     *
     * Returning a blank row instead of a 404 means the screen renders the setup
     * instructions rather than an error - "you have not done this yet" is a
     * state, not a failure.
     */
    @Transactional(readOnly = true)
    public CampusImportSettings forCurrentCampus() {
        Long campusId = currentUser.campusId();
        return repository.findByCampusId(campusId)
                .orElseGet(() -> CampusImportSettings.builder().campusId(campusId).build());
    }

    /**
     * Save the form link and the responses sheet.
     *
     * Both are accepted as URLs or ids. An unparseable sheet reference is
     * refused HERE rather than at the first Pull, because a paste is the moment
     * somebody can still see what they pasted.
     */
    @Transactional
    public CampusImportSettings save(String formUrl, String sheetUrlOrId) {
        Long campusId = currentUser.campusId();

        String sheetId = extractId(sheetUrlOrId);
        if (sheetUrlOrId != null && !sheetUrlOrId.isBlank() && sheetId == null) {
            throw new IllegalArgumentException(
                    "That does not look like a Google Sheets link. Open the responses "
                            + "spreadsheet, copy the address bar, and paste the whole thing.");
        }

        /*
         * A form URL where a sheet belongs is the mistake worth catching by
         * name. Both are docs.google.com links with an id in the same place, so
         * nothing about the shape distinguishes them - only the path does.
         */
        if (sheetUrlOrId != null && sheetUrlOrId.contains("/forms/")) {
            throw new IllegalArgumentException(
                    "That is the form itself, not its responses. In the form open "
                            + "Responses, click the Sheets icon, then copy THAT address.");
        }

        CampusImportSettings settings = repository.findByCampusId(campusId)
                .orElseGet(() -> CampusImportSettings.builder().campusId(campusId).build());

        settings.setFormUrl(trimToNull(formUrl));
        settings.setResponsesSheetId(sheetId);
        settings.setUpdatedBy(currentUser.userId());

        CampusImportSettings saved = repository.save(settings);
        log.info("Import settings for campus {} updated by account {}.",
                campusId, currentUser.userId());
        return saved;
    }

    /** The id from a URL, or the value itself if it already is one. */
    private static String extractId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();

        Matcher matcher = DOC_ID.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return BARE_ID.matcher(trimmed).matches() ? trimmed : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
