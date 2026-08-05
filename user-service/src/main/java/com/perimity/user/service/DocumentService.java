package com.perimity.user.service;

import com.perimity.user.dto.request.DocumentVerificationDto;
import com.perimity.user.dto.response.DocumentResponse;
import com.perimity.user.dto.response.PresignedUrlResponse;
import com.perimity.user.entity.Document;
import com.perimity.user.entity.enums.DocumentType;
import com.perimity.user.exception.ForbiddenException;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.DocumentRepository;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.security.CurrentUser;
import com.perimity.user.storage.StorageException;
import com.perimity.user.storage.StorageKeys;
import com.perimity.user.storage.StorageService;
import com.perimity.user.storage.StoredObject;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documents - a photo, an id proof, a certificate.
 *
 * =========================================================
 *  KEYS ONLY IN THE DATABASE. NO BYTES. That has not changed.
 * =========================================================
 *
 * What changed on Day 9 is where the bytes go. Before, the file was assumed to
 * be in storage already and the caller told us its key. Now this service takes
 * the file itself, puts it in storage, and GENERATES the key.
 *
 * ==================================================================
 *  WHY THE CLIENT-SUPPLIED s3Key HAD TO GO (SRS v1.1)
 * ==================================================================
 *
 * The old endpoint accepted { "userId": 108, "s3Key": "..." }. Nothing stopped
 * a caller naming a path in someone else's folder, so a student could register
 * a row that points at another student's ID proof and then read it back through
 * a perfectly legitimate download. The OBJECT_KEY regex blocked "..", which
 * made the key well formed - it never made it theirs.
 *
 * Server-generated keys close that outright: the key is built from the profile
 * row we just loaded, so it can only ever land under that person's prefix.
 * DocumentCreateDto is deleted rather than deprecated, because a DTO whose
 * whole purpose is a client-supplied storage path should not be one import
 * away from being used again.
 *
 * ============================================
 *  WHY VERIFICATION IS STILL A SEPARATE ACT
 * ============================================
 *
 * Nothing here lets a caller mark their own upload verified. A document is born
 * unverified and only an administrator - and never the owner - can change that.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final StudentProfileRepository studentRepository;
    private final FacultyProfileRepository facultyRepository;
    private final StorageService storage;
    private final UploadValidator uploadValidator;
    private final CurrentUser currentUser;

    private final long maxDocumentBytes;
    private final int presignMinutes;

    public DocumentService(DocumentRepository documentRepository,
                           StudentProfileRepository studentRepository,
                           FacultyProfileRepository facultyRepository,
                           StorageService storage,
                           UploadValidator uploadValidator,
                           CurrentUser currentUser,
                           @Value("${perimity.storage.max-document-mb}") long maxDocumentMb,
                           @Value("${perimity.storage.presign-minutes}") int presignMinutes) {
        this.documentRepository = documentRepository;
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.storage = storage;
        this.uploadValidator = uploadValidator;
        this.currentUser = currentUser;
        this.maxDocumentBytes = maxDocumentMb * 1024 * 1024;
        this.presignMinutes = presignMinutes;
    }

    // ------------------------------------------------------------ upload

    /**
     * Take a file, store it, and record it against a person.
     *
     * The person must have a profile in this service first. That is not
     * bureaucracy: Document has no campus_id of its own, so the profile is the
     * only thing that says which campus this file belongs to - and now also the
     * only thing that says where in storage it may be written.
     */
    @Transactional
    public DocumentResponse upload(Long userId, DocumentType docType, MultipartFile file) {
        currentUser.requireSelfOrStaff(userId);
        Long campusId = requireVisibleHolder(userId);

        // Real type, checked against the file's leading bytes - not the one the
        // browser claimed. See UploadValidator.
        String contentType = uploadValidator.validateDocument(file, maxDocumentBytes);

        String key = StorageKeys.document(campusId, userId, file.getOriginalFilename());
        StoredObject stored = store(key, file, contentType);

        Document document = Document.builder()
                .userId(userId)
                .docType(docType)
                .s3Key(stored.key())
                .fileName(safeFileName(file.getOriginalFilename()))
                .mimeType(contentType)
                .verified(false)
                .build();

        Document saved = documentRepository.save(document);
        log.info("Document {} stored for account {} as {} ({} bytes)",
                saved.getId(), userId, docType, stored.sizeBytes());

        return DocumentResponse.from(saved);
    }

    /**
     * A short-lived link to the file itself.
     *
     * Generated per request and never persisted. The bucket is private and
     * stays private: a permanent public URL cannot be un-shared once it leaks,
     * and what leaks here is somebody's identity document.
     */
    @Transactional(readOnly = true)
    public PresignedUrlResponse downloadUrl(Long id) {
        Document document = require(id);
        currentUser.requireSelfOrStaff(document.getUserId());
        requireVisibleHolder(document.getUserId());

        if (!storage.exists(document.getS3Key())) {
            // The row survived but the object did not - a failed migration, or
            // a bucket wiped in development. Say so plainly rather than handing
            // back a URL that 404s somewhere else.
            throw new ResourceNotFoundException(
                    "Document " + id + " is recorded but its file is missing from storage.");
        }

        return PresignedUrlResponse.of(
                storage.presignedReadUrl(document.getS3Key(), Duration.ofMinutes(presignMinutes)),
                presignMinutes);
    }

    // -------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public List<DocumentResponse> listForUser(Long userId) {
        currentUser.requireSelfOrStaff(userId);
        requireVisibleHolder(userId);

        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listForUserByType(Long userId, DocumentType docType) {
        currentUser.requireSelfOrStaff(userId);
        requireVisibleHolder(userId);

        return documentRepository.findByUserIdAndDocType(userId, docType)
                .stream().map(DocumentResponse::from).toList();
    }

    /** The admin's queue: what on this person still needs checking. */
    @Transactional(readOnly = true)
    public List<DocumentResponse> listPendingForUser(Long userId) {
        currentUser.requireAdministrative();
        requireVisibleHolder(userId);

        return documentRepository.findByUserIdAndVerifiedFalse(userId)
                .stream().map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getOne(Long id) {
        Document document = require(id);
        currentUser.requireSelfOrStaff(document.getUserId());
        requireVisibleHolder(document.getUserId());
        return DocumentResponse.from(document);
    }

    // ------------------------------------------------- verify and reject

    /**
     * An administrator accepts or refuses a document.
     *
     * Three things here are deliberate and each closes a real hole:
     *
     * 1. verifiedBy is taken from the security context and the value in the
     *    body is IGNORED. The DTO still carries the field because it was
     *    written before this service existed, but trusting it would let anyone
     *    record the approval under someone else's name.
     *
     * 2. Nobody may verify their own document. Faculty are administrators on
     *    some campuses; without this check a Campus Admin could approve their
     *    own id proof and the whole step means nothing.
     *
     * 3. A rejection stores its remarks. DocumentVerificationDto already
     *    refuses a rejection with no reason - before Day 6 that text was
     *    validated and then thrown away, so the person was told "rejected" with
     *    no way to learn what to fix and would upload the same file again.
     */
    @Transactional
    public DocumentResponse decide(Long id, DocumentVerificationDto dto) {
        currentUser.requireAdministrative();

        Document document = require(id);
        requireVisibleHolder(document.getUserId());

        Long decidedBy = currentUser.userId();
        if (decidedBy.equals(document.getUserId())) {
            throw new ForbiddenException(
                    "You cannot verify your own document. Ask another administrator.");
        }

        boolean approved = Boolean.TRUE.equals(dto.getVerified());

        document.setVerified(approved);
        document.setVerifiedBy(decidedBy);
        document.setVerifiedAt(LocalDateTime.now());
        // Cleared on approval so an old rejection note never trails a document
        // that has since been accepted.
        document.setVerificationRemarks(approved ? null : trimToNull(dto.getRemarks()));

        Document saved = documentRepository.save(document);
        log.info("Document {} for account {} was {} by {}",
                saved.getId(), saved.getUserId(), approved ? "VERIFIED" : "REJECTED", decidedBy);

        return DocumentResponse.from(saved);
    }

    /**
     * Remove a document record and the file behind it.
     *
     * Only an administrator, and only while it is unverified. A verified
     * document is evidence that somebody checked this person's identity, and
     * deleting it would erase the audit trail that made their pass legitimate.
     * Replace it instead - upload the new file and reject the old one.
     *
     * The object goes AFTER the row is gone. The other order would leave a row
     * pointing at nothing if the transaction then rolled back, and a broken row
     * breaks a screen while an orphaned object costs pennies.
     */
    @Transactional
    public void delete(Long id) {
        Document document = require(id);
        requireVisibleHolder(document.getUserId());

        /*
         * A VERIFIED document is never deletable, by anyone. It is the evidence
         * that somebody checked this person's identity, and the pass issued on
         * the back of it. Replace it, do not erase it.
         */
        if (document.isVerified()) {
            throw new IllegalStateException(
                    "A verified document cannot be deleted. Upload a replacement instead.");
        }

        /*
         * WHO MAY DELETE AN UNVERIFIED DOCUMENT
         *
         * Administrators: any of them, as before.
         *
         * The owner: only their own, and only while it is still AWAITING REVIEW.
         *
         * The case this exists for is the obvious one - a student uploads the
         * wrong file and, until now, could do nothing about it. It stayed on
         * their record, staff reviewed it, and the only remedy was to upload a
         * second file and leave the mistake sitting underneath it.
         *
         * A REJECTED document is deliberately NOT self-deletable even though it
         * is unverified. Its remarks are the reviewer's reasoning, and they are
         * the only thing telling the student what to fix; letting the subject of
         * the decision delete the decision would lose that, and would let a
         * refusal be quietly cleared from their record before anyone else saw
         * it. Rejections are staff's to remove.
         */
        boolean rejected = document.getVerificationRemarks() != null
                && !document.getVerificationRemarks().isBlank();

        if (!currentUser.require().isAdministrative()) {
            currentUser.requireSelfOrStaff(document.getUserId());

            if (rejected) {
                throw new ForbiddenException(
                        "A document that has been reviewed and rejected cannot be removed here. "
                        + "Upload a corrected copy instead.");
            }
        }

        String key = document.getS3Key();
        documentRepository.delete(document);
        storage.delete(key);

        log.info("Unverified document {} for account {} deleted by {}",
                id, document.getUserId(), currentUser.userId());
    }

    // ----------------------------------------------------------- helpers

    private StoredObject store(String key, MultipartFile file, String contentType) {
        try (InputStream in = file.getInputStream()) {
            return storage.put(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new StorageException("Could not read the uploaded file", e);
        }
    }

    /**
     * The original name is kept only so the person recognises their own file in
     * a list. It is never used to build the storage key - StorageKeys does that
     * - so it cannot influence where anything is written.
     */
    private String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "upload";
        }
        String cleaned = original.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "-").trim();
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    /**
     * A document has no campus of its own, so the holder's profile supplies it.
     *
     * @return the campus the holder belongs to, used to build the storage key
     */
    private Long requireVisibleHolder(Long userId) {
        Long campusId = studentRepository.findByUserId(userId)
                .map(p -> p.getCampusId())
                .or(() -> facultyRepository.findByUserId(userId).map(p -> p.getCampusId()))
                .orElseThrow(() -> noProfile(userId));

        if (!currentUser.canSeeCampus(campusId)) {
            // 404, not 403 - a 403 would confirm the person exists on another campus.
            throw noProfile(userId);
        }
        return campusId;
    }

    private ResourceNotFoundException noProfile(Long userId) {
        return new ResourceNotFoundException(
                "Account " + userId + " has no profile in this service yet. "
                        + "Create the student or faculty profile first.");
    }

    private Document require(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Document", id));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
