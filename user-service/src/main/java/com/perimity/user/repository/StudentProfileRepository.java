package com.perimity.user.repository;

import com.perimity.user.entity.StudentProfile;
import java.util.Collection;
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

    /**
     * Many accounts at once, for the batch summary lookup.
     *
     * One query instead of N. A thousand findByUserId calls is a thousand round
     * trips to Postgres for something a single IN clause answers - and the
     * bulk engine asks about a whole batch at a time.
     */
    List<StudentProfile> findByUserIdIn(Collection<Long> userIds);

    boolean existsByUserId(Long userId);

    Optional<StudentProfile> findByCampusIdAndRollNoIgnoreCase(Long campusId, String rollNo);

    Page<StudentProfile> findByCampusIdOrderByIdDesc(Long campusId, Pageable pageable);

    /** The Student Directory filtered by department. Always campus-scoped too. */
    Page<StudentProfile> findByCampusIdAndDepartmentIdOrderByIdDesc(
            Long campusId, Long departmentId, Pageable pageable);

    List<StudentProfile> findByDepartmentId(Long departmentId);

    long countByCampusId(Long campusId);
}
