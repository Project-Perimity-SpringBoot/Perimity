package com.perimity.user.repository;

import com.perimity.user.entity.StudentProfile;
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
}
