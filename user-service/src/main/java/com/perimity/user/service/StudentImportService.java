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
    private final StudentImportBatchRepository batchRepository;
    private final StudentImportRowRepository rowRepository;
    private final StudentProfileRepository studentRepository;
    private final DrivePhotoFetcher photoFetcher;
    private final StorageService storage;
    private final CurrentUser currentUser;
    private final String defaultCountryCode;

    public StudentImportService(ResponseSheetParser parser,
                                ImportRowValidator validator,
                                AuthFeignClient authClient,
                                StudentImportBatchRepository batchRepository,
                                StudentImportRowRepository rowRepository,
                                StudentProfileRepository studentRepository,
                                DrivePhotoFetcher photoFetcher,
                                StorageService storage,
                                CurrentUser currentUser,
                                @Value("${perimity.import.default-country-code:+91}")
                                String defaultCountryCode) {
        this.parser = parser;
        this.validator = validator;
        this.authClient = authClient;
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.studentRepository = studentRepository;
        this.photoFetcher = photoFetcher;
        this.storage = storage;
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
        List<StudentImportRow> usable = rows.stream()
                .filter(r -> r.getOutcome() != ImportRowOutcome.REJECTED)
                .toList();

        if (usable.isEmpty()) {
            batch.setStatus(ImportBatchStatus.COMPLETED);
            batch.setConfirmedAt(LocalDateTime.now());
            batch.setFinishedAt(LocalDateTime.now());
            return batchRepository.save(batch);
        }

        batch.setStatus(ImportBatchStatus.PROCESSING);
        batch.setConfirmedAt(LocalDateTime.now());
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
            batch.setFailureReason(truncate(
                    "Could not reach the accounts service. No accounts were created. "
                            + "Try confirming again once it is back."));
            batch.setFinishedAt(LocalDateTime.now());
            return batchRepository.save(batch);
        }

        Map<Integer, AuthFeignClient.RowResult> byRow = new HashMap<>();
        for (AuthFeignClient.RowResult r : result.results()) {
            if (r.rowNumber() != null) {
                byRow.put(r.rowNumber(), r);
            }
        }

        int created = 0;
        int updated = 0;
        int rejected = batch.getRejectedCount();
        int missingPhoto = 0;

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
            attachPhoto(profile, row, batch.getCampusId());

            if (profile.getPhotoS3Key() == null) {
                missingPhoto++;
            }

            studentRepository.save(profile);

            row.setOutcome(isNew ? ImportRowOutcome.CREATED : ImportRowOutcome.UPDATED);
            row.setMessage(isNew ? "Account created and details verified"
                    : "Existing account updated and details verified");
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
        photoFetcher.fetch(row.getPhotoDriveId()).ifPresent(photo -> {
            try {
                String key = StorageKeys.studentPhoto(
                        campusId, profile.getUserId(), photo.filename());
                StoredObject stored = storage.put(
                        key,
                        new ByteArrayInputStream(photo.bytes()),
                        photo.bytes().length,
                        photo.contentType());
                profile.setPhotoS3Key(stored.key());

            } catch (RuntimeException ex) {
                // Storage failed rather than Drive. Same outcome for the
                // student, and the batch carries on.
                log.warn("Could not store the photo for account {}: {}",
                        profile.getUserId(), ex.getMessage());
            }
        });
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
