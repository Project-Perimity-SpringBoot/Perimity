package com.perimity.user.service;

import com.perimity.user.dto.response.ProfileSummaryBatchResponse;
import com.perimity.user.dto.response.ProfileSummaryResponse;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.storage.StorageService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final int presignMinutes;

    public ProfileLookupService(StudentProfileRepository studentRepository,
                                FacultyProfileRepository facultyRepository,
                                StorageService storage,
                                @Value("${perimity.storage.presign-minutes}") int presignMinutes) {
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.storage = storage;
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
                .map(p -> ProfileSummaryResponse.from(p, photoUrl(p.getPhotoS3Key())));
        if (student.isPresent()) {
            return student;
        }
        return facultyRepository.findByUserId(userId)
                .map(p -> ProfileSummaryResponse.from(p, photoUrl(p.getPhotoS3Key())));
    }

    /** Cheap existence check, for a caller that only needs to know a profile is there. */
    @Transactional(readOnly = true)
    public boolean hasProfile(Long userId) {
        return studentRepository.existsByUserId(userId) || facultyRepository.existsByUserId(userId);
    }

    // -------------------------------------------------------------- batch

    /**
     * Many accounts in one call. Two queries, not N.
     *
     * A MISS IS NOT AN ERROR. The bulk engine creates lightweight VISITOR
     * identities in auth-service for attendees nobody has seen before; those
     * people have an account and a pass and no profile here, because they are
     * not students or staff. Asking about 600 attendees and getting 480 back is
     * the normal result for a mixed sheet.
     *
     * withPhotoUrl is off by default. Signing a URL is local work rather than a
     * network call, but it is a thousand signatures on a full batch for
     * something gatepass does not need when it is only resolving identities.
     * The scanner, which does need a face, asks for one profile at a time.
     *
     * Duplicate ids in the request are collapsed rather than rejected. A caller
     * assembling ids from a spreadsheet will occasionally send the same one
     * twice, and refusing the whole batch over that would be unhelpful; the
     * response is keyed by what was found, so a duplicate simply cannot appear
     * twice in the result.
     */
    @Transactional(readOnly = true)
    public ProfileSummaryBatchResponse summariesOf(List<Long> userIds, boolean withPhotoUrl) {
        Set<Long> wanted = new LinkedHashSet<>(userIds);

        List<ProfileSummaryResponse> found = new ArrayList<>();

        studentRepository.findByUserIdIn(wanted).forEach(p -> found.add(
                ProfileSummaryResponse.from(p, withPhotoUrl ? photoUrl(p.getPhotoS3Key()) : null)));
        facultyRepository.findByUserIdIn(wanted).forEach(p -> found.add(
                ProfileSummaryResponse.from(p, withPhotoUrl ? photoUrl(p.getPhotoS3Key()) : null)));

        Set<Long> resolved = found.stream()
                .map(ProfileSummaryResponse::userId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<Long> missing = wanted.stream().filter(id -> !resolved.contains(id)).toList();

        log.debug("Batch summary: {} requested, {} found, {} without a profile",
                wanted.size(), found.size(), missing.size());

        return ProfileSummaryBatchResponse.of(wanted.size(), found, missing);
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
