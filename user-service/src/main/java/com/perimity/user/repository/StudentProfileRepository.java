package com.perimity.user.repository;

import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.ProfileVerificationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    /** The common lookup: resolve a login account to its student profile. */
    Optional<StudentProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Optional<StudentProfile> findByCampusIdAndRollNoIgnoreCase(Long campusId, String rollNo);

    Page<StudentProfile> findByCampusIdOrderByIdDesc(Long campusId, Pageable pageable);

    /** The Student Directory filtered by department. Always campus-scoped too. */
    Page<StudentProfile> findByCampusIdAndDepartmentIdOrderByIdDesc(
            Long campusId, Long departmentId, Pageable pageable);

    List<StudentProfile> findByDepartmentId(Long departmentId);

    long countByCampusId(Long campusId);

    /**
     * The faculty review queue: students on this campus waiting for a decision.
     *
     * Ordered by submittedAt ASCENDING - oldest first. The default id-descending
     * order used by the directory would put the newest submission at the top and
     * bury whoever has been waiting longest at the bottom of the last page,
     * which is how a queue turns into a backlog nobody clears.
     *
     * Campus-scoped in the query itself, not filtered afterwards. Faculty on one
     * campus must never see another campus's students, and a scope applied after
     * the page has been cut would return short pages as a side effect of the
     * filtering.
     */
    Page<StudentProfile> findByCampusIdAndVerificationStatusOrderBySubmittedAtAsc(
            Long campusId, ProfileVerificationStatus verificationStatus, Pageable pageable);

    long countByCampusIdAndVerificationStatus(
            Long campusId, ProfileVerificationStatus verificationStatus);
}
