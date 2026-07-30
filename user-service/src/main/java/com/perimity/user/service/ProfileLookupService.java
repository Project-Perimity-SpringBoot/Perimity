package com.perimity.user.service;

import com.perimity.user.dto.response.ProfileSummaryResponse;
import com.perimity.user.exception.ResourceNotFoundException;
import com.perimity.user.repository.FacultyProfileRepository;
import com.perimity.user.repository.StudentProfileRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one read other services are allowed to make.
 *
 * =====================================================================
 *  THIS CLASS DELIBERATELY DOES NOT TOUCH CurrentUser. Do not add it.
 * =====================================================================
 *
 * Every other service class here starts by asking who the caller is. This one
 * cannot: the caller is gatepass-service issuing a pass, possibly from a queue
 * consumer, and there is no human and no JWT on the request. Calling
 * CurrentUser.require() here would throw ForbiddenException on every internal
 * call and the failure would look like a permissions bug rather than a design
 * mistake.
 *
 * What replaces the user check is the InternalApiKeyFilter in front of it. That
 * is also why this service is reachable ONLY through UserInternalController and
 * why nothing else should call it - a public endpoint wired to these methods
 * would have no ownership check at all.
 *
 * The response is deliberately narrow. gatepass-service needs an identifier and
 * a photo key to put on a printed pass. It does not need an address or a
 * government id, and the narrower this contract stays the less there is to
 * break when either side changes.
 */
@Service
public class ProfileLookupService {

    private final StudentProfileRepository studentRepository;
    private final FacultyProfileRepository facultyRepository;

    public ProfileLookupService(StudentProfileRepository studentRepository,
                                FacultyProfileRepository facultyRepository) {
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
    }

    /**
     * Resolve an account to whichever profile it has.
     *
     * Student is checked first only because there are far more of them; the two
     * tables share a unique constraint on user_id each, and nothing allows one
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
        return studentRepository.findByUserId(userId)
                .map(ProfileSummaryResponse::from)
                .or(() -> facultyRepository.findByUserId(userId).map(ProfileSummaryResponse::from));
    }

    /** Cheap existence check, for a caller that only needs to know a profile is there. */
    @Transactional(readOnly = true)
    public boolean hasProfile(Long userId) {
        return studentRepository.existsByUserId(userId) || facultyRepository.existsByUserId(userId);
    }
}
