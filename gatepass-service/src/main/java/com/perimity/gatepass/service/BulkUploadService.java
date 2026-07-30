package com.perimity.gatepass.service;

import com.perimity.gatepass.bulk.BulkValidationService;
import com.perimity.gatepass.bulk.ErrorReportWriter;
import com.perimity.gatepass.bulk.ParsedRow;
import com.perimity.gatepass.bulk.SheetParser;
import com.perimity.gatepass.client.InternalServiceClient;
import com.perimity.gatepass.dto.request.BulkConfirmDto;
import com.perimity.gatepass.dto.response.BulkUploadBatchResponse;
import com.perimity.gatepass.dto.response.BulkValidationSummaryResponse;
import com.perimity.gatepass.dto.response.PageResponse;
import com.perimity.gatepass.entity.BulkUploadBatch;
import com.perimity.gatepass.entity.Event;
import com.perimity.gatepass.entity.GatePass;
import com.perimity.gatepass.entity.enums.BatchStatus;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.entity.enums.PassType;
import com.perimity.gatepass.exception.ResourceNotFoundException;
import com.perimity.gatepass.messaging.QrJobPublisher;
import com.perimity.gatepass.repository.BulkUploadBatchRepository;
import com.perimity.gatepass.repository.EventRepository;
import com.perimity.gatepass.repository.GatePassRepository;
import com.perimity.gatepass.storage.StorageKeys;
import com.perimity.gatepass.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The bulk engine. One engine for students and event visitors, exactly as the
 * Event & Bulk design document specifies - passType and the source of the dates
 * are the only things that differ.
 *
 * ==========================================================================
 *  TWO PHASES, AND THE REASON FOR EACH.
 * ==========================================================================
 *
 * PHASE ONE - validate (fast, synchronous, ~2 seconds, faculty is waiting)
 *   store the sheet -> parse -> validate every row -> write errors.csv ->
 *   status VALIDATED -> return "580 valid, 20 errors"
 *   NOTHING IS CREATED. No identities, no passes, no emails.
 *
 * PHASE TWO - confirm (slow, asynchronous, faculty can close the browser)
 *   re-read the sheet -> re-validate -> resolve each row to an identity ->
 *   create N passes -> publish N jobs -> status PROCESSING -> return immediately
 *
 * Generating 600 QRs, 600 PDFs and 600 emails inside the HTTP request would
 * time out the browser and leave half of them done with no record of which
 * half. RabbitMQ decouples it so the faculty never waits and one failure never
 * blocks the rest.
 *
 * ==========================================================================
 *  WHY CONFIRM RE-PARSES INSTEAD OF PERSISTING ROWS
 * ==========================================================================
 *
 * The obvious design writes every parsed row to a bulk_upload_rows table during
 * phase one so phase two can read them back. That is an extra entity, an extra
 * repository, an extra table and 600 inserts of data that is ALREADY stored -
 * immutably, under a key saved on the batch row.
 *
 * Re-reading is also more correct. The second pass re-runs the blocklist check,
 * so somebody barred in the minutes between Validate and Confirm is caught.
 * Persisted rows would have frozen the stale answer and issued them a pass.
 *
 * The cost is parsing the sheet twice. For a 1000-row cap that is well under a
 * second, and it buys away a whole table.
 */
@Service
public class BulkUploadService {

    private static final Logger log = LoggerFactory.getLogger(BulkUploadService.class);

    /** Campus config key. Arham's CampusConfigDefaults seeds this at 1000. */
    private static final String CONFIG_MAX_ROWS = "bulk.upload.max.rows";
    private static final int MAX_ROWS_FALLBACK = 1000;

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * The first bytes of every .xlsx file. An xlsx is a ZIP archive, so it
     * starts with PK\003\004. Checked because the browser-supplied content type
     * is a claim, not a fact - the same reasoning as Arham's logo upload.
     */
    private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};

    private final BulkUploadBatchRepository batchRepository;
    private final GatePassRepository passRepository;
    private final EventRepository eventRepository;
    private final StorageService storage;
    private final SheetParser parser;
    private final BulkValidationService validator;
    private final ErrorReportWriter errorReportWriter;
    private final InternalServiceClient internal;
    private final QrJobPublisher qrJobPublisher;
    private final long maxSheetBytes;
    private final int presignMinutes;

    public BulkUploadService(BulkUploadBatchRepository batchRepository,
                             GatePassRepository passRepository,
                             EventRepository eventRepository,
                             StorageService storage,
                             SheetParser parser,
                             BulkValidationService validator,
                             ErrorReportWriter errorReportWriter,
                             InternalServiceClient internal,
                             QrJobPublisher qrJobPublisher,
                             @Value("${perimity.storage.max-sheet-mb}") long maxSheetMb,
                             @Value("${perimity.storage.presign-minutes}") int presignMinutes) {
        this.batchRepository = batchRepository;
        this.passRepository = passRepository;
        this.eventRepository = eventRepository;
        this.storage = storage;
        this.parser = parser;
        this.validator = validator;
        this.errorReportWriter = errorReportWriter;
        this.internal = internal;
        this.qrJobPublisher = qrJobPublisher;
        this.maxSheetBytes = maxSheetMb * 1024 * 1024;
        this.presignMinutes = presignMinutes;
    }

    // ============================================================ phase one

    /**
     * Upload and validate. Creates the batch row and nothing else.
     *
     * @param eventId required when passType is EVENT, must be null otherwise -
     *                the same rule BulkUploadInitDto enforces for the JSON
     *                shape, restated here because this entry point is multipart
     *                and the DTO's cross-field checks never run on it.
     */
    @Transactional
    public BulkValidationSummaryResponse validate(MultipartFile file,
                                                  Long campusId,
                                                  Long uploadedBy,
                                                  PassType passType,
                                                  Long eventId) {

        requireCoherentType(passType, eventId);
        requireUsableFile(file);

        Event event = null;
        if (passType == PassType.EVENT) {
            event = eventRepository.findByIdAndCampusId(eventId, campusId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Event", eventId));
            if (event.isCancelled()) {
                throw new IllegalStateException("That event has been cancelled.");
            }
            if (event.getValidTo().isBefore(LocalDate.now())) {
                throw new IllegalStateException(
                        "That event finished on " + event.getValidTo()
                                + ". Passes cannot be issued for it.");
            }
        }

        String campusCode = internal.campusOf(campusId)
                .map(InternalServiceClient.CampusView::code)
                .orElse("campus-" + campusId);

        // The sheet is stored BEFORE it is parsed. If parsing then fails, the
        // file that caused it is still on disk and can be looked at, which is
        // the difference between "it said unreadable" and being able to fix it.
        String stagingKey = StorageKeys.bulkStaging(campusCode, file.getOriginalFilename());
        store(stagingKey, file);

        BulkUploadBatch batch = batchRepository.save(BulkUploadBatch.builder()
                .campusId(campusId)
                .uploadedBy(uploadedBy)
                .passType(passType)
                .eventId(eventId)
                .objectKey(stagingKey)
                .originalFilename(file.getOriginalFilename())
                .status(BatchStatus.VALIDATING)
                .build());

        int maxRows = maxRowsFor(campusId);

        List<ParsedRow> rows;
        try (InputStream in = storage.openStream(stagingKey)) {
            rows = parser.parse(in, maxRows);
        } catch (SheetParser.UnreadableSheetException e) {
            // The FILE is bad, not a row. FAILED, with the reason on the row so
            // the screen can show it instead of a bare red box.
            batch.setStatus(BatchStatus.FAILED);
            batch.setFailureMessage(e.getMessage());
            batch.setCompletedAt(LocalDateTime.now());
            batchRepository.save(batch);
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded sheet", e);
        }

        BulkValidationService.Outcome outcome = validator.validate(rows, campusId, eventId);

        String errorKey = null;
        if (!outcome.errors().isEmpty()) {
            errorKey = StorageKeys.bulkErrorReport(campusCode, batch.getId());
            byte[] csv = errorReportWriter.write(outcome.errors());
            storage.put(errorKey, new ByteArrayInputStream(csv), csv.length,
                    ErrorReportWriter.CONTENT_TYPE);
        }

        batch.setTotalRows(outcome.totalRows());
        batch.setValidRows(outcome.validCount());
        batch.setInvalidRows(outcome.invalidCount());
        batch.setErrorReportKey(errorKey);
        batch.setStatus(BatchStatus.VALIDATED);

        // Every row was rejected. Still VALIDATED rather than FAILED - the file
        // was perfectly readable, it just has nothing usable in it, and the
        // uploader needs the error report to find out why.
        if (outcome.validCount() == 0) {
            batch.setFailureMessage("No row in that sheet could be accepted. "
                    + "Download the error report to see why.");
            batch.setCompletedAt(LocalDateTime.now());
        }

        batchRepository.save(batch);

        log.info("Batch {} validated: {} of {} row(s) usable",
                batch.getId(), outcome.validCount(), outcome.totalRows());

        return BulkValidationSummaryResponse.from(batch, outcome.inlineErrors());
    }

    // ============================================================ phase two

    /**
     * Confirm. Resolves identities, creates passes, queues generation.
     *
     * Returns as soon as the rows are queued. Everything after that happens on
     * the RabbitMQ side and is watched through the progress endpoint.
     */
    @Transactional
    public BulkUploadBatchResponse confirm(Long campusId, Long batchId, BulkConfirmDto dto) {

        BulkUploadBatch batch = require(campusId, batchId);

        // Idempotent. Double-clicking Confirm must not issue 1200 passes.
        if (batch.getStatus() == BatchStatus.PROCESSING
                || batch.getStatus() == BatchStatus.COMPLETED) {
            log.info("Batch {} already confirmed, returning current state", batchId);
            return BulkUploadBatchResponse.from(batch);
        }

        if (batch.getStatus() != BatchStatus.VALIDATED) {
            throw new IllegalStateException(
                    "Only a validated batch can be confirmed. This one is "
                            + batch.getStatus().name().toLowerCase() + ".");
        }

        if (batch.getValidRows() == 0) {
            throw new IllegalStateException(
                    "There is nothing to confirm - no row in that sheet was accepted.");
        }

        Event event = null;
        if (batch.getPassType() == PassType.EVENT) {
            event = eventRepository.findByIdAndCampusId(batch.getEventId(), campusId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Event", batch.getEventId()));
            // Re-checked here, not just at validate. An event cancelled while
            // the summary sat on screen must not still release 600 passes.
            if (event.isCancelled()) {
                throw new IllegalStateException(
                        "That event was cancelled after this sheet was validated.");
            }
        }

        List<ParsedRow> rows;
        try (InputStream in = storage.openStream(batch.getObjectKey())) {
            rows = parser.parse(in, maxRowsFor(campusId));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "The uploaded sheet could no longer be read. Please upload it again.", e);
        }

        // Second validation pass - see the class comment on why.
        BulkValidationService.Outcome outcome =
                validator.validate(rows, campusId, batch.getEventId());

        batch.setStatus(BatchStatus.PROCESSING);
        batch.setProcessedRows(0);
        batchRepository.save(batch);

        List<GatePass> created = new ArrayList<>();

        for (ParsedRow row : outcome.valid()) {
            try {
                created.add(createPassForRow(row, batch, event));
            } catch (RuntimeException e) {
                // NEVER let one row take down the batch. Same rule as the
                // expiry sweep and the validation pass.
                log.error("Batch {} row {} ({}) could not be issued: {}",
                        batch.getId(), row.rowNumber(), row.emailKey(), e.getMessage());
            }
        }

        batch.setValidRows(outcome.validCount());
        batch.setProcessedRows(created.size());
        batchRepository.save(batch);

        // Published after commit, per row, for exactly the reason QrJobPublisher
        // documents: qr-service must never read a pass before its INSERT is
        // visible.
        created.forEach(qrJobPublisher::publishAfterCommit);

        log.info("Batch {} confirmed by user {}: {} pass(es) queued",
                batch.getId(), dto.getConfirmedBy(), created.size());

        return BulkUploadBatchResponse.from(batch);
    }

    /**
     * One row -> one identity -> one pass.
     *
     * THE MIXED-ATTENDEE RESOLUTION, and the whole point of the engine. The
     * faculty uploading 600 rows does not know which 102 of them are already
     * students. auth-service answers that per row, by email:
     *
     *   email already known  -> reuse that identity, issue only an EVENT pass
     *   email brand new      -> create a lightweight VISITOR identity, then the pass
     *
     * Omkar's POST /api/internal/auth/users does both behind one idempotent
     * call, so there is no "check then create" race here and no duplicate
     * accounts.
     */
    private GatePass createPassForRow(ParsedRow row, BulkUploadBatch batch, Event event) {

        Long holderUserId = internal.resolveOrCreateIdentity(
                        row.emailKey(),
                        row.name(),
                        row.phone(),
                        batch.getCampusId(),
                        "gatepass-bulk-batch-" + batch.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "auth-service could not resolve an identity for " + row.emailKey()));

        boolean forEvent = batch.getPassType() == PassType.EVENT;

        GatePass pass = GatePass.builder()
                .holderUserId(holderUserId)
                .holderName(row.name().trim())
                .campusId(batch.getCampusId())
                .passType(batch.getPassType())
                .eventId(batch.getEventId())
                .batchId(batch.getId())
                // Event batch: the event's own window applies to every row, not
                // a per-row date. Student batch: starts today and never ends,
                // which is what validTo = null means on a DAILY pass.
                .validFrom(forEvent ? event.getValidFrom() : LocalDate.now())
                .validTo(forEvent ? event.getValidTo() : null)
                .status(PassStatus.PENDING)
                .build();

        return passRepository.save(pass);
    }

    /**
     * qr-service reports a row finished. Bumps the counter the progress screen
     * reads, and closes the batch when the last one lands.
     *
     * REQUIRES_NEW because this is called from the RabbitMQ listener, once per
     * message. Sharing a transaction with the activation would mean a failed
     * activation silently rolls back the progress count too, and the bar would
     * stick at 412 of 580 forever with no explanation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRowCompleted(Long batchId) {
        if (batchId == null) {
            return;
        }

        BulkUploadBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null || batch.getStatus() != BatchStatus.PROCESSING) {
            return;
        }

        long done = passRepository.countByBatchIdAndStatus(batchId, PassStatus.ACTIVE);
        batch.setProcessedRows((int) Math.min(done, batch.getValidRows()));

        if (done >= batch.getValidRows()) {
            batch.setStatus(BatchStatus.COMPLETED);
            batch.setCompletedAt(LocalDateTime.now());
            log.info("Batch {} completed - all {} pass(es) generated", batchId, done);
        }

        batchRepository.save(batch);
    }

    // ============================================================== reads

    @Transactional(readOnly = true)
    public BulkUploadBatchResponse getOne(Long campusId, Long batchId) {
        return BulkUploadBatchResponse.from(require(campusId, batchId));
    }

    @Transactional(readOnly = true)
    public PageResponse<BulkUploadBatchResponse> list(Long campusId, Pageable pageable) {
        return PageResponse.from(
                batchRepository.findByCampusIdOrderByCreatedAtDesc(campusId, pageable),
                BulkUploadBatchResponse::from);
    }

    /** Short-lived link to errors.csv. The bucket itself stays private. */
    @Transactional(readOnly = true)
    public String errorReportUrl(Long campusId, Long batchId) {
        BulkUploadBatch batch = require(campusId, batchId);

        if (batch.getErrorReportKey() == null) {
            throw new ResourceNotFoundException(
                    "Batch " + batchId + " has no error report - every row was accepted.");
        }
        return storage.presignedReadUrl(batch.getErrorReportKey(),
                Duration.ofMinutes(presignMinutes));
    }

    // ============================================================= helpers

    /**
     * The row limit is a per-campus POLICY, read from campus-service, not a
     * constant in this file. A campus that runs 3000-person convocations sets
     * its own number without a redeploy.
     *
     * Falls back if campus-service is unreachable - the same fail-soft posture
     * InternalServiceClient takes everywhere else.
     */
    private int maxRowsFor(Long campusId) {
        return internal.configInt(campusId, CONFIG_MAX_ROWS, MAX_ROWS_FALLBACK);
    }

    private void requireCoherentType(PassType passType, Long eventId) {
        if (passType == PassType.EVENT && eventId == null) {
            throw new IllegalArgumentException("An event batch must name an event.");
        }
        if (passType == PassType.DAILY && eventId != null) {
            throw new IllegalArgumentException(
                    "A student batch cannot be linked to an event. Student passes have no "
                            + "end date; event passes take the event's dates.");
        }
    }

    /**
     * Four checks, cheapest first - the same order and the same reasoning as
     * Arham's CampusAssetService.validate.
     */
    private void requireUsableFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (file.getSize() > maxSheetBytes) {
            throw new IllegalArgumentException(
                    "The sheet must be smaller than " + (maxSheetBytes / 1024 / 1024) + " MB.");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException(
                    "The file must be an .xlsx spreadsheet. Save as Excel Workbook and "
                            + "upload again.");
        }
        if (!looksLikeXlsx(file)) {
            throw new IllegalArgumentException(
                    "That file is not really an .xlsx, whatever it is named.");
        }
    }

    /** An xlsx is a ZIP. These are the first four bytes of every ZIP. */
    private boolean looksLikeXlsx(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(4);
            return java.util.Arrays.equals(head, ZIP_MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    private void store(String key, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            storage.put(key, in, file.getSize(), XLSX_CONTENT_TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the uploaded sheet", e);
        }
    }

    private BulkUploadBatch require(Long campusId, Long batchId) {
        return batchRepository.findByIdAndCampusId(batchId, campusId)
                .orElseThrow(() -> ResourceNotFoundException.of("Batch", batchId));
    }
}
