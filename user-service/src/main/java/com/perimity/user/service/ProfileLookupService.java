package com.perimity.user.service;

import com.perimity.user.dto.response.ProfileSummaryResponse;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.storage.StorageService;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reads other services are allowed to make.
 *
 * =====================================================================
 *  THIS CLASS DELIBERATELY DOES NOT TOUCH CurrentUser. Do not add it.
 * =====================================================================
 *
 * Every other service class here starts by asking who the caller is. This one
 * cannot: the caller is gatepass-service issuing a pass or guard-service
 * resolving a scan, possibly from a queue consumer, and there is no human and
 * no JWT on the request. Calling CurrentUser.require() here would throw
 * ForbiddenException on every internal call and the failure would look like a
 * permissions bug rather than a design mistake.
 *
 * What replaces the user check is the InternalApiKeyFilter in front of it. That
 * is also why this service is reachable ONLY through UserInternalController and
 * why nothing else should call it - a public endpoint wired to these methods
 * would have no ownership check at all.
 *
 * The response is deliberately narrow: an identifier, a campus, a photo. No
 * address, no government id. The narrower this contract stays, the less there
 * is to leak and the less there is to break when either side changes.
 */
@Service
public class ProfileLookupService {

    private static final Logger log = LoggerFactory.getLogger(ProfileLookupService.class);

    private final StudentProfileRepository studentRepository;
    private final FacultyProfileRepository facultyRepository;
    private final StorageService storage;
    /*
     * ProfileGuard, and NOT CurrentUser-flavoured anything - see the class note.
     * Only departmentName() is used here, which is a plain repository read with
     * no caller in it. requireSelectableDepartment() must never be called from
     * this class: it is a write-path rule about what staff may choose, and an
     * internal read has nobody to refuse.
     */
    private final ProfileGuard guard;
    private final int presignMinutes;

    public ProfileLookupService(StudentProfileRepository studentRepository,
                                FacultyProfileRepository facultyRepository,
                                StorageService storage,
                                ProfileGuard guard,
                                @Value("${perimity.storage.presign-minutes}") int presignMinutes) {
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.storage = storage;
        this.guard = guard;
        this.presignMinutes = presignMinutes;
    }

    // ------------------------------------------------------------- single

    /**
     * Resolve an account to whichever profile it has.
     *
     * Student is checked first only because there are far more of them; each
     * table has its own unique constraint on user_id and nothing allows one
     * account to hold both, so the order changes performance and nothing else.
     *
     * Throws rather than returning an empty body when there is no profile.
     * gatepass-service treats any failure as "carry on without the photo"
     * (see its InternalServiceClient), so a 404 here degrades to a pass without
     * a picture rather than a pass that cannot be issued.
     */
    @Transactional(readOnly = true)
    public ProfileSummaryResponse summaryOf(Long userId) {
        return findSummary(userId).orElseThrow(() -> new ResourceNotFoundException(
                "No student or faculty profile exists for account " + userId));
    }

    @Transactional(readOnly = true)
    public Optional<ProfileSummaryResponse> findSummary(Long userId) {
        Optional<ProfileSummaryResponse> student = studentRepository.findByUserId(userId)
                .map(p -> ProfileSummaryResponse.from(p, photoUrl(p.getPhotoS3Key()),
                        guard.departmentName(p.getDepartmentId())));
        if (student.isPresent()) {
            return student;
        }
        return facultyRepository.findByUserId(userId)
                .map(p -> ProfileSummaryResponse.from(p, photoUrl(p.getPhotoS3Key()),
                        guard.departmentName(p.getDepartmentId())));
    }

    // ----------------------------------------------------------- helpers

    /**
     * A short-lived signed link to the photo, or null when there is none.
     *
     * NEVER STORED, minted per read. Persisting one would recreate the
     * permanent public link the whole mechanism exists to avoid.
     *
     * Swallows storage failures on purpose. This is enrichment: a scanner that
     * shows a name and no face is degraded, a scanner that returns 500 because
     * S3 was slow is broken - and it is broken at a gate with a queue behind
     * it. Same fail-soft reasoning gatepass applies to its own internal calls.
     */
    private String photoUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return storage.presignedReadUrl(key, Duration.ofMinutes(presignMinutes));
        } catch (RuntimeException ex) {
            log.warn("Could not sign a photo URL for key {}: {}", key, ex.getMessage());
            return null;
        }
    }
}
