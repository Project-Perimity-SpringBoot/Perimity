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
import com.perimity.gatepass.entity.VisitorRequest;
import com.perimity.gatepass.entity.enums.BatchStatus;
import com.perimity.gatepass.entity.enums.Gender;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.entity.enums.PassType;
import com.perimity.gatepass.entity.enums.PurposeType;
import com.perimity.gatepass.entity.enums.RequestStatus;
import com.perimity.gatepass.entity.enums.VisitorType;
import com.perimity.gatepass.exception.ResourceNotFoundException;
import com.perimity.gatepass.messaging.QrJobPublisher;
import com.perimity.gatepass.repository.BulkUploadBatchRepository;
import com.perimity.gatepass.repository.EventRepository;
import com.perimity.gatepass.repository.GatePassRepository;
import com.perimity.gatepass.repository.VisitorRequestRepository;
import com.perimity.gatepass.storage.StorageKeys;
import com.perimity.gatepass.storage.StorageService;
import com.perimity.gatepass.validation.ValidationPatterns;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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

    /**
     * The phone rule VisitorRequest ITSELF enforces - deliberately not the one
     * BulkValidationService uses.
     *
     * ======================================================================
     *  THESE TWO RULES DO NOT AGREE, AND THAT IS NOT A BUG TO FIX HERE
     * ======================================================================
     * A sheet row is checked against ValidationPatterns.PHONE, which is
     * campus-agnostic: an optional +, then 7 to 15 digits. VisitorRequest
     * carries PHONE_IN, which is India-only: exactly ten digits starting 6-9.
     * So "78757386666" is a perfectly valid row AND an invalid visitor record.
     *
     * Loosening the entity would weaken a rule the manual visitor form relies
     * on. Tightening the sheet would reject attendees for having a foreign
     * number, in a product that is supposed to be campus-agnostic. So neither
     * moves: the number is left off the visitor record when it does not fit,
     * and it survives on the identity in auth-service either way, which is
     * where anyone would look for it.
     */
    private static final Pattern VISITOR_RECORD_PHONE = Pattern.compile(ValidationPatterns.PHONE_IN);

    private final BulkUploadBatchRepository batchRepository;
    private final GatePassRepository passRepository;
    private final EventRepository eventRepository;
    private final StorageService storage;
    private final SheetParser parser;
    private final BulkValidationService validator;
    private final ErrorReportWriter errorReportWriter;
    private final InternalServiceClient internal;
    private final QrJobPublisher qrJobPublisher;
    private final VisitorRequestRepository visitorRequestRepository;
    private final Validator beanValidator;
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
                             VisitorRequestRepository visitorRequestRepository,
                             // NOT named "validator": that field is already taken by
                             // BulkValidationService, which checks sheet ROWS. This one
                             // checks a built ENTITY against its own annotations.
                             Validator beanValidator,
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
        this.visitorRequestRepository = visitorRequestRepository;
        this.beanValidator = beanValidator;
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

        InternalServiceClient.UserView holder = internal.resolveOrCreateIdentity(
                        row.emailKey(),
                        row.name(),
                        row.phone(),
                        batch.getCampusId(),
                        "gatepass-bulk-batch-" + batch.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "auth-service could not resolve an identity for " + row.emailKey()));

        boolean forEvent = batch.getPassType() == PassType.EVENT;

        /*
         * ==================================================================
         *  MEMBER OR GUEST - DECIDED BY THE ROLE AUTH-SERVICE HANDED BACK
         * ==================================================================
         * An attendee whose email already belongs to a STUDENT (or any other
         * campus role) is a member attending an event. They keep the account,
         * the profile and the standing pass they already have, and this batch
         * adds ONE MORE pass to their dashboard - the event one. Nothing about
         * their identity is touched, because a spreadsheet typed by whoever ran
         * the form is not a better source of truth than their own profile.
         *
         * An attendee auth-service had never seen is now a VISITOR: a
         * lightweight identity with no password, who signs in with a one-time
         * code. They have no profile anywhere, so the details the form did
         * collect are recorded here as a visitor request - already APPROVED,
         * because faculty uploading the roster IS the approval. Without it the
         * guard scanning them at the gate has a pass with a name on it and
         * nothing else.
         */
        Long visitorRequestId = null;
        if (forEvent && isGuest(holder.role())) {
            visitorRequestId = recordGuestDetails(row, batch, event, holder);
        }

        GatePass pass = GatePass.builder()
                .holderUserId(holder.id())
                .holderName(row.name().trim())
                .campusId(batch.getCampusId())
                .passType(batch.getPassType())
                .eventId(batch.getEventId())
                .batchId(batch.getId())
                .visitorRequestId(visitorRequestId)
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
     * Null-safe, and treats an unrecognised role as a member rather than a
     * guest.
     *
     * Getting this backwards in the safe direction matters: mistaking a member
     * for a guest writes a visitor record for somebody who already has a
     * profile, which is confusing but harmless. Mistaking a guest for a member
     * loses their details entirely, and there is nowhere else to recover them
     * from.
     */
    private boolean isGuest(String role) {
        return "VISITOR".equalsIgnoreCase(role);
    }

    /**
     * Writes the guest's form answers as an APPROVED visitor request.
     *
     * IDEMPOTENT BY EMAIL AND EVENT. Confirming a batch is retryable, and the
     * faculty may upload a corrected sheet for the same event that repeats most
     * of the same people. Neither may leave the same guest holding two
     * identical visitor records, so an existing one for this email at this
     * event is reused.
     *
     * Only the fields a visitor record actually has are carried across. Roll
     * number, department and address have no column here - they belong to a
     * student profile, which a guest does not have - so they stay in the stored
     * sheet rather than being written somewhere they do not fit. The photo link
     * is a Google Drive URL, not an image; fetching those bytes needs the Drive
     * service account that only user-service holds, so photoKey stays null and
     * the guard falls back to the name on the pass.
     */
    private Long recordGuestDetails(ParsedRow row, BulkUploadBatch batch, Event event,
                                    InternalServiceClient.UserView holder) {

        VisitorRequest existing = visitorRequestRepository
                .findByVisitorEmailOrderByCreatedAtDesc(row.emailKey())
                .stream()
                .filter(r -> event.getId().equals(r.getEventId()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            return existing.getId();
        }

        ParsedRow.Details details = row.details();

        VisitorRequest request = VisitorRequest.builder()
                .campusId(batch.getCampusId())
                .visitorName(row.name().trim())
                .visitorEmail(row.emailKey())
                // Both of these drop a value the entity would reject rather
                // than passing it through and letting Hibernate throw. See
                // VISITOR_RECORD_PHONE and pastDateOnly.
                .visitorPhone(phoneTheRecordAccepts(row.phone()))
                .purpose(row.purpose() != null ? row.purpose() : "Attending " + event.getName())
                .purposeType(PurposeType.EVENT)
                .visitorType(VisitorType.GUEST)
                .gender(details == null ? null : parseGender(details.gender()))
                .dateOfBirth(details == null ? null : pastDateOnly(parseDate(details.dateOfBirth())))
                .eventId(event.getId())
                .hostUserId(batch.getUploadedBy())
                .visitFrom(event.getValidFrom())
                .visitTo(event.getValidTo())
                .visitorUserId(holder.id())
                // Faculty uploading the roster is the approval. Leaving these
                // PENDING would put every attendee of a 600-person event into
                // somebody's review queue for no decision anyone intends to make.
                .status(RequestStatus.APPROVED)
                .reviewedBy(batch.getUploadedBy())
                .reviewedAt(LocalDateTime.now())
                // NOT otp-verified. They have not proved they hold the mailbox
                // yet; they do that the first time they sign in.
                .otpVerified(false)
                .build();

        /*
         * ==================================================================
         *  CHECKED HERE SO THAT HIBERNATE NEVER GETS THE CHANCE TO THROW
         * ==================================================================
         * confirm() is one transaction around the whole batch, and the per-row
         * try/catch around this call CANNOT save it: a constraint violation
         * inside a transaction marks it rollback-only, the catch swallows the
         * exception, the loop cheerfully carries on, and then the commit fails
         * with UnexpectedRollbackException. Every pass in the batch is lost,
         * including the rows that were fine. That is exactly what happened the
         * first time this ran - one eleven-digit phone number cost the whole
         * upload.
         *
         * So the entity is validated in plain Java BEFORE it is handed to the
         * repository. The two sanitisers above deal with the mismatches that
         * are known; this catches the ones that are not, and it does it without
         * ever touching the session.
         *
         * An invalid record is dropped, not fatal. The pass is the thing the
         * attendee needs; the form answers are a bonus, and losing them is a
         * log line rather than a person turned away at the gate.
         */
        Set<ConstraintViolation<VisitorRequest>> violations = beanValidator.validate(request);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            log.warn("Row {} ({}): visitor details not recorded - {}. The event pass is still issued.",
                    row.rowNumber(), row.emailKey(), detail);
            return null;
        }

        return visitorRequestRepository.save(request).getId();
    }

    /**
     * The phone number, but only if the visitor record will accept it.
     *
     * Returning null loses nothing that matters: auth-service already holds
     * this person's phone against their identity, under the campus-agnostic
     * rule, which is the copy anyone actually looks up.
     */
    private String phoneTheRecordAccepts(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        if (VISITOR_RECORD_PHONE.matcher(trimmed).matches()) {
            return trimmed;
        }
        log.debug("Phone \"{}\" does not fit the visitor record's format; left blank", trimmed);
        return null;
    }

    /**
     * A date of birth, but only if it is actually in the past.
     *
     * dateOfBirth is @Past, and a Google Form with a free date field collects
     * next month's date more often than anyone expects - somebody picks the
     * default and moves on. That is a typo in a nice-to-have field, not a
     * reason to withhold a pass, so it is dropped quietly.
     */
    private LocalDate pastDateOnly(LocalDate date) {
        if (date == null || date.isBefore(LocalDate.now())) {
            return date;
        }
        log.debug("Date of birth {} is not in the past; left blank", date);
        return null;
    }

    /**
     * "Male", "female", "F", "Prefer not to say" -> the enum, or null.
     *
     * A free-text answer that does not map is NOT an error. Gender is recorded
     * if the form asked for it in a way we can read, and left blank otherwise;
     * rejecting a row over it would keep somebody out of an event because their
     * form offered an option this enum does not have.
     */
    private Gender parseGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("m")) {
            return Gender.MALE;
        }
        if (value.startsWith("f") || value.startsWith("w")) {
            return Gender.FEMALE;
        }
        if (value.startsWith("o") || value.startsWith("t") || value.startsWith("n")) {
            return Gender.OTHER;
        }
        return null;
    }

    /**
     * A date cell, or null.
     *
     * SheetParser already renders a date-formatted cell as an ISO date, which
     * is the case this handles. A date typed as free text in some local format
     * is deliberately NOT guessed at: 03/04/2005 is two different dates on two
     * different continents, and a campus-agnostic product does not get to pick
     * one. Unparseable means blank, never a wrong date of birth.
     */
    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            log.debug("Date of birth \"{}\" is not an ISO date; left blank", raw);
            return null;
        }
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

    /**
     * Re-queue only the rows of a batch whose generation never finished.
     *
     * A broker restart, an OOM in qr-service, or a poison message that hit the
     * dead-letter queue leaves passes at PENDING with nobody coming back for
     * them. Before this, the only recovery was to re-upload the whole
     * spreadsheet - which creates a SECOND pass for all 580 people who were
     * fine, and puts a second QR in each of their inboxes.
     *
     * PENDING is the definition of "never finished": ACTIVE means qr-service
     * reported success, and REVOKED or EXPIRED means someone or something has
     * since made the row moot. Only PENDING is genuinely unfinished work.
     *
     * Idempotent by construction. Re-running it on a batch with nothing pending
     * publishes nothing and returns zero, so an impatient admin clicking Retry
     * four times does no damage.
     */
    @Transactional
    public Map<String, Object> retryFailedRows(Long campusId, Long batchId) {

        BulkUploadBatch batch = require(campusId, batchId);

        if (batch.getStatus() != BatchStatus.PROCESSING
                && batch.getStatus() != BatchStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Only a batch that has been confirmed can be retried. This one is "
                            + batch.getStatus().name().toLowerCase() + ".");
        }

        List<GatePass> stuck =
                passRepository.findByBatchIdAndStatus(batchId, PassStatus.PENDING);

        if (stuck.isEmpty()) {
            return Map.of("batchId", batchId, "requeued", 0,
                    "message", "Nothing to retry - every pass in this batch has been generated.");
        }

        // Back to PROCESSING. A batch marked COMPLETED that turns out to have
        // stragglers is not complete, and leaving the status alone would mean
        // recordRowCompleted never fires again for it.
        batch.setStatus(BatchStatus.PROCESSING);
        batch.setCompletedAt(null);
        batchRepository.save(batch);

        stuck.forEach(qrJobPublisher::publishAfterCommit);

        log.info("Batch {} - re-queued {} stuck pass(es)", batchId, stuck.size());

        return Map.of("batchId", batchId, "requeued", stuck.size(),
                "message", "Re-queued " + stuck.size() + " row(s) that had not finished.");
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
