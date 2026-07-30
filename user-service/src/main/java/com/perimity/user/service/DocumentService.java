package com.perimity.user.service;

import com.perimity.user.dto.request.DocumentCreateDto;
import com.perimity.user.dto.request.DocumentVerificationDto;
import com.perimity.user.dto.response.DocumentResponse;
import com.perimity.user.entity.Document;
import com.perimity.user.entity.enums.DocumentType;
import com.perimity.user.exception.ForbiddenException;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.DocumentRepository;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documents - a photo, an id proof, a certificate.
 *
 * =========================================================
 *  KEYS ONLY. NO BYTES. Not one method here takes a byte[].
 * =========================================================
 *
 * The file is uploaded to object storage first; this service records that it
 * exists, who it belongs to, and whether an admin has since checked it. Putting
 * file bytes in Postgres would put a scanned ID proof in every database backup,
 * every replica and every developer's laptop dump, and would grow the row size
 * of the table every profile screen reads.
 *
 * ============================================
 *  WHY VERIFICATION IS A SEPARATE ACT
 * ============================================
 *
 * DocumentCreateDto has no "verified" field, on purpose: if a client could send
 * verified = true it would approve its own id proof and the verification step
 * would exist only as decoration. So a document is always born unverified, and
 * only an administrator can change that.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /**
     * What a document is allowed to claim to be.
     *
     * This checks the CLIENT-DECLARED type only. Renaming a file takes one
     * second, so on Day 9 - when the upload path exists - the real content type
     * of the stored object must be read back and checked against this same set
     * before the row is trusted. Until then this stops the honest mistakes
     * (a .docx CV, a 40 MB video) and nothing more. It is not a security
     * control yet, and this comment is here so nobody mistakes it for one.
     */
    private static final Set<String> ALLOWED_DOCUMENT_TYPES =
            Set.of("application/pdf", "image/jpeg", "image/png");

    /** A profile photo is an image. A PDF headshot helps nobody. */
    private static final Set<String> ALLOWED_PHOTO_TYPES =
            Set.of("image/jpeg", "image/png");

    private final DocumentRepository documentRepository;
    private final StudentProfileRepository studentRepository;
    private final FacultyProfileRepository facultyRepository;
    private final CurrentUser currentUser;

    public DocumentService(DocumentRepository documentRepository,
                           StudentProfileRepository studentRepository,
                           FacultyProfileRepository facultyRepository,
                           CurrentUser currentUser) {
        this.documentRepository = documentRepository;
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.currentUser = currentUser;
    }

    // ---------------------------------------------------------- register

    /**
     * Record a file that has already been uploaded to object storage.
     *
     * The person must have a profile in this service first. That is not
     * bureaucracy: Document has no campus_id of its own, so the profile is the
     * only thing that says which campus this file belongs to. Without the
     * lookup there would be no way to stop one campus's admin from reading
     * another campus's id proofs.
     */
    @Transactional
    public DocumentResponse register(DocumentCreateDto dto) {
        currentUser.requireSelfOrStaff(dto.getUserId());
        requireVisibleHolder(dto.getUserId());

        String mimeType = trimToNull(dto.getMimeType());
        requireAcceptableType(dto.getDocType(), mimeType);

        Document document = Document.builder()
                .userId(dto.getUserId())
                .docType(dto.getDocType())
                .s3Key(dto.getS3Key().trim())
                .fileName(dto.getFileName().trim())
                .mimeType(mimeType)
                .verified(false)
                .build();

        Document saved = documentRepository.save(document);
        log.info("Document {} registered for account {} as {}",
                saved.getId(), saved.getUserId(), saved.getDocType());

        return DocumentResponse.from(saved);
    }

    // -------------------------------------------------------------- read

    /** Everything this person has uploaded, newest first. */
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
     * Remove a document record.
     *
     * Only an administrator, and only while it is unverified. A verified
     * document is evidence that somebody checked this person's identity, and
     * deleting it would erase the audit trail that made their pass legitimate.
     * Replace it instead - register the new file and reject the old one.
     *
     * The object in storage is NOT deleted here. Storage cleanup arrives with
     * the upload path on Day 9; deleting the row now and the object later is
     * the right order, because an orphaned object costs pennies and an orphaned
     * row breaks a screen.
     */
    @Transactional
    public void delete(Long id) {
        currentUser.requireAdministrative();

        Document document = require(id);
        requireVisibleHolder(document.getUserId());

        if (document.isVerified()) {
            throw new IllegalStateException(
                    "A verified document cannot be deleted. Register a replacement instead.");
        }
        documentRepository.delete(document);
        log.info("Unverified document {} for account {} deleted by {}",
                id, document.getUserId(), currentUser.userId());
    }

    // ----------------------------------------------------------- helpers

    private void requireAcceptableType(DocumentType docType, String mimeType) {
        if (mimeType == null) {
            // Tolerated for now: the client may genuinely not know. Day 9 reads
            // the real type off the stored object, and this becomes mandatory.
            return;
        }
        Set<String> allowed = docType == DocumentType.PHOTO
                ? ALLOWED_PHOTO_TYPES
                : ALLOWED_DOCUMENT_TYPES;

        if (!allowed.contains(mimeType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "A " + docType.name().toLowerCase().replace('_', ' ')
                            + " must be one of " + String.join(", ", allowed)
                            + ", not " + mimeType + ".");
        }
    }

    /**
     * A document has no campus of its own, so the holder's profile supplies it.
     *
     * An account with no profile in this service is refused rather than allowed
     * through: there would be nothing to scope the file to, and "no profile
     * yet" is the correct answer to give.
     */
    private void requireVisibleHolder(Long userId) {
        Long campusId = studentRepository.findByUserId(userId)
                .map(p -> p.getCampusId())
                .or(() -> facultyRepository.findByUserId(userId).map(p -> p.getCampusId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account " + userId + " has no profile in this service yet. "
                                + "Create the student or faculty profile first."));

        if (!currentUser.canSeeCampus(campusId)) {
            // 404, not 403 - a 403 would confirm the person exists on another campus.
            throw new ResourceNotFoundException(
                    "Account " + userId + " has no profile in this service yet. "
                            + "Create the student or faculty profile first.");
        }
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
