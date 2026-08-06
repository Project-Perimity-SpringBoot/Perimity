package com.perimity.user.service;

import com.perimity.user.bulk.DrivePhotoFetcher;
import com.perimity.user.bulk.ImportRowValidator;
import com.perimity.user.bulk.ResponseSheetParser;
import com.perimity.user.bulk.TemporaryPasswords;
import com.perimity.user.client.AuthFeignClient;
import com.perimity.user.dto.response.ImportBatchResponse;
import com.perimity.user.dto.response.ImportRowResponse;
import com.perimity.user.dto.response.PageResponse;
import com.perimity.user.storage.StorageKeys;
import com.perimity.user.storage.StorageService;
import com.perimity.user.storage.StoredObject;
import java.io.ByteArrayInputStream;
import com.perimity.user.entity.CampusImportSettings;
import com.perimity.user.entity.StudentImportBatch;
import com.perimity.user.entity.StudentImportRow;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.ImportBatchStatus;
import com.perimity.user.entity.enums.ImportRowOutcome;
import com.perimity.user.entity.enums.ProfileVerificationStatus;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.StudentImportBatchRepository;
import com.perimity.user.repository.StudentImportRowRepository;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk student onboarding from a Google Form responses sheet.
 *
 * See docs/BULK_STUDENT_ONBOARDING.md.
 *
 * ==========================================================================
 * TWO REQUESTS, AND THE GAP BETWEEN THEM IS THE POINT
 * ==========================================================================
 * validate() reads the sheet and writes NOTHING but the batch record. confirm()
 * is a separate call a person has to make after reading the preview.
 *
 * That pause is not politeness. Rows import as VERIFIED, and verifiedBy records
 * whoever confirmed - so there has to be a moment where a named human looked at
 * the data and took responsibility for it. Writing straight from the upload
 * would make the verification record name somebody who never saw the rows,
 * which is the false attestation this whole feature exists to prevent.
 *
 * It also catches the ordinary disaster: the wrong file, last term's sheet, a
 * renamed column - before it becomes two hundred accounts.
 *
 * ==========================================================================
 * WHY VERIFIED AT ALL
 * ==========================================================================
 * A form submission is self-declared and unchecked; on its own it is weaker
 * evidence than the in-app flow, where faculty read each profile.
 *
 * What makes VERIFIED honest here is the uploader. They chose the file, saw the
 * rows and confirmed. That is a real person taking responsibility for a batch -
 * weaker than reading every row, and a trade made deliberately, but it is not a
 * lie. verifiedBy must never be null or a system id on this path.
 */
@Service
public class StudentImportService {

    private static final Logger log = LoggerFactory.getLogger(StudentImportService.class);

    private final ResponseSheetParser parser;
    private final ImportRowValidator validator;
    private final AuthFeignClient authClient;
    private final com.perimity.user.client.GatepassFeignClient gatepassClient;
    private final StudentImportBatchRepository batchRepository;
    private final StudentImportRowRepository rowRepository;
    private final StudentProfileRepository studentRepository;
    private final DrivePhotoFetcher photoFetcher;
    private final StorageService storage;
    private final ImportSettingsService settingsService;
    private final CurrentUser currentUser;
    private final String defaultCountryCode;

    public StudentImportService(ResponseSheetParser parser,
                                ImportRowValidator validator,
                                AuthFeignClient authClient,
                                com.perimity.user.client.GatepassFeignClient gatepassClient,
                                StudentImportBatchRepository batchRepository,
                                StudentImportRowRepository rowRepository,
                                StudentProfileRepository studentRepository,
                                DrivePhotoFetcher photoFetcher,
                                StorageService storage,
                                ImportSettingsService settingsService,
                                CurrentUser currentUser,
                                @Value("${perimity.import.default-country-code:+91}")
                                String defaultCountryCode) {
        this.parser = parser;
        this.validator = validator;
        this.authClient = authClient;
        this.gatepassClient = gatepassClient;
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.studentRepository = studentRepository;
        this.photoFetcher = photoFetcher;
        this.storage = storage;
        this.settingsService = settingsService;
        this.currentUser = currentUser;
        this.defaultCountryCode = defaultCountryCode;
    }

    // ------------------------------------------------------------ validate

    /**
     * Parse and check a sheet. Writes the batch and its rows; creates no
     * accounts and touches no profiles.
     */
    @Transactional
    public StudentImportBatch validate(MultipartFile file) {
        Long campusId = currentUser.campusId();
        Long uploader = currentUser.userId();

        StudentImportBatch batch = batchRepository.save(StudentImportBatch.builder()
                .campusId(campusId)
                .uploadedBy(uploader)
                .filename(file.getOriginalFilename())
                .status(ImportBatchStatus.VALIDATING)
                .build());

        ResponseSheetParser.ParseResult parsed;
        try {
            parsed = parser.parse(file);
        } catch (ResponseSheetParser.SheetException ex) {
            // The sheet is unusable, which is a batch failure rather than a row
            // failure - there are no rows to report against.
            batch.setStatus(ImportBatchStatus.FAILED);
            batch.setFailureReason(truncate(ex.getMessage()));
            batch.setFinishedAt(LocalDateTime.now());
            return batchRepository.save(batch);
        }

        if (!parsed.usable()) {
            /*
             * Missing columns are named ALL AT ONCE. Reporting the first one
             * would turn fixing a sheet into a guessing game played one upload
             * at a time.
             */
            String missing = parsed.missingColumns().stream()
                    .map(c -> c.name().toLowerCase().replace('_', ' '))
                    .reduce((a, b) -> a + ", " + b).orElse("");
            batch.setStatus(ImportBatchStatus.FAILED);
            batch.setFailureReason(truncate(
                    "The sheet has no column for: " + missing
                            + ". Check the form questions against the ones this import expects."));
            batch.setFinishedAt(LocalDateTime.now());
            return batchRepository.save(batch);
        }

        List<StudentImportRow> rows =
                validator.validate(batch.getId(), campusId, parsed.rows(), defaultCountryCode);
        rowRepository.saveAll(rows);

        int rejected = (int) rows.stream()
                .filter(r -> r.getOutcome() == ImportRowOutcome.REJECTED).count();

        batch.setTotalRows(rows.size());
        batch.setValidRows(rows.size() - rejected);
        batch.setRejectedCount(rejected);
        batch.setStatus(ImportBatchStatus.VALIDATED);

        log.info("Batch {}: {} rows, {} valid, {} rejected, from {}",
                batch.getId(), rows.size(), rows.size() - rejected, rejected, batch.getFilename());

        return batchRepository.save(batch);
    }

    /**
     * Validate the campus's responses sheet straight from Drive.
     *
     * ==================================================================
     *  THE SAME CODE PATH AS AN UPLOAD, DELIBERATELY
     * ==================================================================
     * Drive exports the sheet as .xlsx and it goes through the identical
     * parser and validator. Nothing about "pulled" versus "uploaded" is
     * special-cased anywhere downstream, so a bug cannot exist on one route
     * and not the other - which is exactly what would happen if pulling had
     * its own reader.
     *
     * It exists because downloading a file and immediately uploading it again
     * is a round trip the server can do itself, and every manual step is a
     * chance to import last month's copy from a downloads folder.
     */
    @Transactional
    public StudentImportBatch validateFromDrive() {
        CampusImportSettings settings = settingsService.forCurrentCampus();

        if (!settings.isComplete()) {
            throw new IllegalStateException(
                    "This campus has no intake form set up yet. Add the form link and "
                            + "its responses sheet first.");
        }
        if (!photoFetcher.isUsable()) {
            // Distinguished from a missing sheet: one is a setup step somebody
            // has to do, the other is a deployment that has no Drive access at
            // all, and telling them apart is the difference between a fixable
            // problem and a confusing one.
            throw new IllegalStateException(
                    "Google Drive is not available on this server, so responses cannot be "
                            + "pulled. Download the sheet and upload it instead.");
        }

        byte[] workbook = photoFetcher.exportSheet(settings.getResponsesSheetId())
                .orElseThrow(() -> new IllegalStateException(
                        "Could not read the responses sheet. Check it is shared with the "
                                + "service account, and that the link points at the "
                                + "responses spreadsheet rather than the form."));

        return validate(new DriveWorkbook(workbook, "responses.xlsx"));
    }

    /**
     * The exported bytes wrapped as a MultipartFile.
     *
     * A shim, so validate() takes one type and neither it nor the parser has to
     * know where a workbook came from. The alternative - a second validate()
     * overload taking bytes - is two code paths that must be kept identical
     * forever, which is a thing nobody manages.
     */
    private record DriveWorkbook(byte[] content, String name) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public java.io.InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }
        @Override public void transferTo(java.io.File dest) throws java.io.IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }

    /**
     * Whether this server can reach Drive at all.
     *
     * Drives what the screen offers rather than what it allows - a Pull button
     * that always fails is worse than no Pull button, because the second tells
     * you to download the sheet instead.
     */
    public boolean driveAvailable() {
        return photoFetcher.isUsable();
    }

    /** The raw .xlsx, for the Download button. */
    @Transactional(readOnly = true)
    public byte[] downloadResponsesSheet() {
        CampusImportSettings settings = settingsService.forCurrentCampus();
        if (!settings.isComplete()) {
            throw new IllegalStateException("This campus has no intake form set up yet.");
        }
        return photoFetcher.exportSheet(settings.getResponsesSheetId())
                .orElseThrow(() -> new IllegalStateException(
                        "Could not read the responses sheet. Check it is shared with the "
                                + "service account."));
    }

    // ---------------------------------------------------------------- read

    /**
     * One batch, campus-scoped.
     *
     * findByIdAndCampusId rather than findById, and the difference is not
     * cosmetic: a bare id lookup would let a faculty member on one campus poll
     * another campus's import - and the counts alone leak how many students
     * that campus is onboarding.
     *
     * Another campus's batch reads as "not found" rather than "forbidden",
     * because a 403 confirms the batch exists.
     */
    @Transactional(readOnly = true)
    public StudentImportBatch getBatch(Long batchId) {
        return batchRepository.findByIdAndCampusId(batchId, currentUser.campusId())
                .orElseThrow(() -> ResourceNotFoundException.of("Import batch", batchId));
    }

    /**
     * The rows of a batch, optionally filtered by outcome.
     *
     * getBatch first, so the campus check happens before any row is read. Going
     * straight to the rows would answer for a batch the caller may not see.
     */
    @Transactional(readOnly = true)
    public PageResponse<ImportRowResponse> rows(Long batchId,
                                                ImportRowOutcome outcome,
                                                Pageable pageable) {
        StudentImportBatch batch = getBatch(batchId);

        var page = outcome == null
                ? rowRepository.findByBatchIdOrderByRowNumberAsc(batch.getId(), pageable)
                : rowRepository.findByBatchIdAndOutcomeOrderByRowNumberAsc(
                        batch.getId(), outcome, pageable);

        return PageResponse.from(page, ImportRowResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportBatchResponse> listBatches(Pageable pageable) {
        return PageResponse.from(
                batchRepository.findByCampusIdOrderByIdDesc(currentUser.campusId(), pageable),
                ImportBatchResponse::from);
    }

    // ------------------------------------------------------------- confirm

    /**
     * Create the accounts and fill in the profiles.
     *
     * ==================================================================
     *  SYNCHRONOUS, FOR NOW, AND THAT WILL HAVE TO CHANGE
     * ==================================================================
     * Accounts go over in ONE call, and the profiles are local updates, so a
     * two hundred row batch is fast enough to answer inline today.
     *
     * It will not stay that way. Drive photo fetches, pass issue and email are
     * per-student network calls, and a batch doing those needs to run in the
     * background with the progress screen polling PROCESSING. The batch model
     * already has the states for it. Moving it later is a change to this method
     * and not to the model, which is why the model was built that way first.
     */
    @Transactional
    public StudentImportBatch confirm(Long batchId) {
        StudentImportBatch batch = batchRepository
                .findByIdAndCampusId(batchId, currentUser.campusId())
                .orElseThrow(() -> ResourceNotFoundException.of("Import batch", batchId));

        if (!batch.getStatus().isConfirmable()) {
            throw new IllegalStateException(
                    "This batch is " + batch.getStatus() + " and cannot be confirmed. "
                            + "Only a validated batch can be.");
        }

        List<StudentImportRow> rows = rowRepository.findByBatchIdOrderByRowNumberAsc(batchId);

        /*
         * PENDING only - not "everything that is not REJECTED".
         *
         * This is what makes a failed confirm resumable. A batch that died
         * partway has rows already marked CREATED or UPDATED, and those are
         * finished: their accounts exist and their profiles are written.
         * Re-processing them would be harmless (auth-service matches on email,
         * and applyRow is idempotent) but it would also re-count them, so a
         * resumed batch would report more students than the sheet contains.
         *
         * Taking only PENDING means confirm picks up exactly where it stopped.
         */
        List<StudentImportRow> usable = rows.stream()
                .filter(r -> r.getOutcome() == ImportRowOutcome.PENDING)
                .toList();

        boolean resuming = batch.getStatus() == ImportBatchStatus.FAILED;
        if (resuming) {
            log.info("Batch {} is being resumed. {} of {} rows still to process.",
                    batchId, usable.size(), rows.size());
        }

        if (usable.isEmpty()) {
            /*
             * Nothing left to do. Either every row was rejected at validation,
             * or this is a resume of a batch that actually finished its work
             * before failing to report it - which is exactly the case that used
             * to leave an operator unable to tell what had landed.
             */
            batch.setStatus(ImportBatchStatus.COMPLETED);
            batch.setFailureReason(null);
            if (batch.getConfirmedAt() == null) {
                batch.setConfirmedAt(LocalDateTime.now());
            }
            batch.setFinishedAt(LocalDateTime.now());
            return batchRepository.save(batch);
        }

        batch.setStatus(ImportBatchStatus.PROCESSING);
        // Preserved across a resume - the first confirm is when a person took
        // responsibility, and that is the moment the verification record means.
        if (batch.getConfirmedAt() == null) {
            batch.setConfirmedAt(LocalDateTime.now());
        }
        batch.setFailureReason(null);
        batchRepository.save(batch);

        /*
         * A password per row, generated here and never reused. These are the
         * values that get emailed; auth-service sets mustChangePassword so each
         * one survives exactly one sign-in.
         *
         * Held only for the length of this call. Nothing writes them to the
         * import row - a table of live credentials sitting next to the students
         * they belong to is not a thing to create.
         */
        Map<String, String> passwords = new HashMap<>();
        List<AuthFeignClient.StudentBatchRequest.Row> accountRows = new ArrayList<>(usable.size());

        for (StudentImportRow row : usable) {
            String password = TemporaryPasswords.generate();
            passwords.put(row.getEmail(), password);
            accountRows.add(new AuthFeignClient.StudentBatchRequest.Row(
                    row.getRowNumber(),
                    row.getEmail(),
                    displayName(row),
                    null,
                    password));
        }

        AuthFeignClient.StudentBatchResult result;
        try {
            result = authClient.createStudents(new AuthFeignClient.StudentBatchRequest(
                    batch.getCampusId(), batch.getUploadedBy(),
                    "student-import-batch-" + batch.getId(), accountRows)).data();
        } catch (RuntimeException ex) {
            /*
             * auth-service unreachable. The batch failed as a whole - no
             * accounts were made, so nothing is half-done - and it can be
             * confirmed again once the service is back.
             *
             * Deliberately NOT left in PROCESSING: a batch stuck in a
             * transitional state is one nobody can retry or clear.
             */
            /*
             * The ROOT cause, not ex.getMessage().
             *
             * spring.cloud.openfeign.circuitbreaker.enabled=true wraps every
             * Feign client, so a client with no fallback surfaces every failure
             * as NoFallbackAvailableException("No fallback available") - which
             * says nothing about whether the host was unreachable, the key was
             * rejected, or the call timed out. The real exception is underneath.
             */
            log.error("Batch {} could not reach auth-service: {}", batchId, rootCause(ex), ex);
            batch.setStatus(ImportBatchStatus.FAILED);

            /*
             * The message must not claim nothing happened.
             *
             * It used to say "No accounts were created", and that was a guess
             * dressed as a fact - the very first real import proved it wrong.
             * The call timed out on this side while auth-service went ahead and
             * created the account, so the batch reported FAILED and a student
             * existed.
             *
             * What is actually true is that this ATTEMPT did not complete. Rows
             * already written keep their outcomes, and confirming again resumes
             * from whatever is still PENDING.
             */
            int done = batch.getCreatedCount() + batch.getUpdatedCount();
            batch.setFailureReason(truncate(
                    "The accounts service did not answer in time. "
                            + (done > 0
                                    ? done + " student(s) were already imported and are kept. "
                                    : "")
                            + usable.size() + " row(s) are still waiting. "
                            + "Confirm again to carry on from where it stopped - "
                            + "nothing will be duplicated."));
            batch.setFinishedAt(LocalDateTime.now());
            return batchRepository.save(batch);
        }

        Map<Integer, AuthFeignClient.RowResult> byRow = new HashMap<>();
        for (AuthFeignClient.RowResult r : result.results()) {
            if (r.rowNumber() != null) {
                byRow.put(r.rowNumber(), r);
            }
        }

        /*
         * Seeded from the batch, not from zero.
         *
         * On a resume, rows written by the earlier attempt are no longer in
         * `usable`, so counting from zero would report only the second half of
         * the work and silently lose the first. These carry forward instead.
         */
        int created = batch.getCreatedCount();
        int updated = batch.getUpdatedCount();
        int rejected = batch.getRejectedCount();
        int missingPhoto = batch.getMissingPhotoCount();

        for (StudentImportRow row : usable) {
            AuthFeignClient.RowResult accountResult = byRow.get(row.getRowNumber());

            if (accountResult == null || accountResult.userId() == null) {
                row.setOutcome(ImportRowOutcome.REJECTED);
                row.setMessage(accountResult == null
                        ? "The accounts service did not answer for this row."
                        : "The accounts service refused this row (" + accountResult.outcome() + ").");
                rejected++;
                continue;
            }

            row.setUserId(accountResult.userId());
            boolean isNew = "CREATED".equals(accountResult.outcome());

            /*
             * The profile already exists - auth-service published user.created
             * and the listener provisioned an empty one. This fills it in.
             *
             * findByUserId rather than create: racing the listener would hit the
             * unique index on user_id. If the event has not landed yet, the row
             * is created here instead, and the listener's existence check makes
             * its own attempt a no-op. Either order works.
             */
            StudentProfile profile = studentRepository.findByUserId(accountResult.userId())
                    .orElseGet(() -> StudentProfile.builder()
                            .userId(accountResult.userId())
                            .campusId(batch.getCampusId())
                            .build());

            applyRow(profile, row, batch.getUploadedBy());

            /*
             * Outcome message FIRST, then the photo.
             *
             * attachPhoto appends to this message when it cannot get an image,
             * so setting it afterwards would overwrite the explanation with a
             * cheerful "Account created" and leave the count unexplained again
             * - the exact problem this reporting was added to fix.
             */
            row.setOutcome(isNew ? ImportRowOutcome.CREATED : ImportRowOutcome.UPDATED);
            row.setMessage(isNew
                    ? "Account created and details verified"
                    : "Existing account updated and details verified");

            attachPhoto(profile, row, batch.getCampusId());

            if (profile.getPhotoS3Key() == null) {
                missingPhoto++;
            }

            studentRepository.save(profile);

            try {
                gatepassClient.issuePass(new com.perimity.user.client.GatepassFeignClient.IssuePassRequest(
                        accountResult.userId(),
                        displayName(row),
                        batch.getCampusId(),
                        null,
                        "DAILY",
                        null,
                        java.time.LocalDate.now(),
                        null
                ));
                log.info("Issued standing DAILY pass for imported student {} (user {})", displayName(row), accountResult.userId());
            } catch (Exception ex) {
                log.error("Could not issue DAILY pass for student {} (user {}): {}", displayName(row), accountResult.userId(), ex.getMessage());
            }

            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        rowRepository.saveAll(rows);

        batch.setCreatedCount(created);
        batch.setUpdatedCount(updated);
        batch.setRejectedCount(rejected);
        batch.setMissingPhotoCount(missingPhoto);
        batch.setStatus(ImportBatchStatus.COMPLETED);
        batch.setFinishedAt(LocalDateTime.now());

        log.info("Batch {} finished: {} created, {} updated, {} rejected, {} without a photo.",
                batchId, created, updated, rejected, missingPhoto);

        return batchRepository.save(batch);
    }

    // ----------------------------------------------------------- helpers

    /**
     * Copies the row onto the profile and marks it verified against the
     * uploader.
     *
     * rollNo and departmentId are set here because a staff member supplied them
     * through the form they designed - unlike the in-app flow, where a student
     * must not choose their own roll number.
     */
    private void applyRow(StudentProfile profile, StudentImportRow row, Long verifiedBy) {
        profile.setFirstName(row.getFirstName());
        profile.setMiddleName(row.getMiddleName());
        profile.setLastName(row.getLastName());
        profile.setDateOfBirth(row.getDateOfBirth());
        profile.setGender(row.getGender());
        profile.setAddress(row.getAddress());
        profile.setPhoneCountryCode(row.getPhoneCountryCode());
        profile.setPhoneNumber(row.getPhoneNumber());
        profile.setRollNo(row.getRollNo());
        profile.setDepartmentId(row.getDepartmentId());

        profile.setVerificationStatus(ProfileVerificationStatus.VERIFIED);
        profile.setVerifiedBy(verifiedBy);
        profile.setVerifiedAt(LocalDateTime.now());
        profile.setSubmittedAt(LocalDateTime.now());
        profile.setVerificationRemarks(null);
    }

    /**
     * Pulls this row's photo from Drive and stores it against the profile.
     *
     * ==================================================================
     *  SILENT ON FAILURE, AND THAT IS THE POINT
     * ==================================================================
     * Drive being off, unreachable, or holding something that is not an image
     * all end the same way: no photo on this profile, the row still imported,
     * the batch's missingPhotoCount incremented. Those students appear on the
     * progress screen as needing one, and no pass issues until they upload it
     * in the app.
     *
     * The alternative - failing the row - would mean a Google outage during an
     * intake costs a hundred students their accounts. The account and the
     * verified details are worth having on their own; the photo can arrive
     * later, and the rule that a pass needs one is enforced where passes are
     * issued rather than here.
     *
     * An existing photo is NOT replaced. On a re-import, a student who has
     * already uploaded a better picture in the app keeps it - the sheet is a
     * starting point, not the authority.
     */
    private void attachPhoto(StudentProfile profile, StudentImportRow row, Long campusId) {
        if (profile.getPhotoS3Key() != null || row.getPhotoDriveId() == null) {
            return;
        }

        var photo = photoFetcher.fetch(row.getPhotoDriveId());

        if (photo.isEmpty()) {
            /*
             * SAID OUT LOUD, on the row.
             *
             * This used to be a log line and a number: faculty saw "3 imported
             * without a photo" and had no way to tell whether the folder was
             * unshared, a link was dead, or somebody uploaded a PDF. Those need
             * different fixes, and a count cannot distinguish them.
             *
             * The row already carries a message field that validation failures
             * use, so a person opening the preview sees this the same way they
             * see every other problem - against the student it belongs to.
             */
            row.setMessage(appendNote(row.getMessage(),
                    photoFetcher.isUsable()
                            ? "photo could not be read from Drive - check the responses "
                                    + "folder is shared, and that the file is an image"
                            : "no photo: Google Drive is not available on this server"));
            return;
        }

        try {
            String key = StorageKeys.studentPhoto(
                    campusId, profile.getUserId(), photo.get().filename());
            StoredObject stored = storage.put(
                    key,
                    new ByteArrayInputStream(photo.get().bytes()),
                    photo.get().bytes().length,
                    photo.get().contentType());
            profile.setPhotoS3Key(stored.key());

        } catch (RuntimeException ex) {
            // Storage failed rather than Drive - a different cause, and worth
            // saying so, because re-sharing a Drive folder will not fix it.
            log.warn("Could not store the photo for account {}: {}",
                    profile.getUserId(), ex.getMessage());
            row.setMessage(appendNote(row.getMessage(),
                    "photo downloaded but could not be saved"));
        }
    }

    /**
     * Adds a note to a row's message without losing what was already there.
     *
     * A row can be imported AND have a photo problem, so the outcome message
     * ("Account created and details verified") has to survive alongside it.
     * Overwriting would trade one piece of information for another.
     */
    private static String appendNote(String existing, String note) {
        String combined = (existing == null || existing.isBlank())
                ? note
                : existing + " — " + note;
        return truncate(combined);
    }

    /**
     * The name for the ACCOUNT, which is what a pass carries.
     *
     * The form's "full name" answer wins when there is one, because it is what
     * the student wrote as their own name. The three parts joined are the
     * fallback - accurate, but assembled by us rather than chosen by them.
     */
    private String displayName(StudentImportRow row) {
        if (row.getFullName() != null && !row.getFullName().isBlank()) {
            return row.getFullName().trim();
        }
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{row.getFirstName(), row.getMiddleName(), row.getLastName()}) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }

    /**
     * The deepest cause with a message, so a wrapped failure names itself.
     *
     * Bounded at ten hops rather than looping until getCause() is null: a
     * self-referencing cause chain is rare but real, and an infinite loop
     * inside error handling is a worse failure than the one being reported.
     */
    private static String rootCause(Throwable ex) {
        Throwable current = ex;
        for (int i = 0; i < 10 && current.getCause() != null; i++) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }
}
