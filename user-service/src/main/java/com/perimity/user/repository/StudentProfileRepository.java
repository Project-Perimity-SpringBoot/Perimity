package com.perimity.user.repository;

import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.ProfileVerificationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    /** The common lookup: resolve a login account to its student profile. */
    Optional<StudentProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /**
     * Create the empty profile for an account only if one does not exist yet,
     * and NEVER throw when it does.
     *
     * ==================================================================
     *  WHY THIS IS A NATIVE UPSERT AND NOT find-then-save
     * ==================================================================
     * Two things create a student profile and they run at the same time: the
     * user.created listener provisions an empty one off the queue, and the
     * caller that just made the account (Add Student, or a bulk import
     * confirming a batch) fills one in over HTTP. "Look it up, insert if it is
     * missing" has a window between the SELECT and the INSERT, and the listener
     * lands in it - uk_student_user fires, the caller gets a 409, and in an
     * import the whole batch's transaction rolls back after the accounts were
     * already created in auth-service.
     *
     * Catching the violation afterwards does not help: Hibernate marks the
     * transaction rollback-only when a constraint fails, so everything done
     * after the catch fails again at commit. The race has to be lost inside the
     * database, not recovered from in Java - which is exactly what ON CONFLICT
     * DO NOTHING does.
     *
     * No conflict target named on purpose. Only user_id and campus_id are
     * written here (roll_no stays null and is not part of any unique tuple that
     * can collide), so the only constraint that can fire is uk_student_user -
     * and the untargeted form is the one H2's PostgreSQL mode understands, so
     * the tests run the same statement production does.
     *
     * verification_status is written explicitly rather than left to the entity
     * default, because a native insert bypasses @Builder.Default. DRAFT is
     * right: nobody has checked anything yet.
     *
     * @return 1 when a row was inserted, 0 when one already existed. Both are
     *         success - the caller re-reads either way.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO student_profiles
                (user_id, campus_id, verification_status, created_at, updated_at)
            VALUES (:userId, :campusId, 'DRAFT', now(), now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("campusId") Long campusId);

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
