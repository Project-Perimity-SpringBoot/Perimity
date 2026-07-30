package com.perimity.user.repository;

import com.perimity.user.entity.FacultyProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FacultyProfileRepository extends JpaRepository<FacultyProfile, Long> {

    Optional<FacultyProfile> findByUserId(Long userId);

    /** Many accounts at once, for the batch summary lookup. See the student repository. */
    List<FacultyProfile> findByUserIdIn(Collection<Long> userIds);

    boolean existsByUserId(Long userId);

    Optional<FacultyProfile> findByCampusIdAndEmployeeIdIgnoreCase(Long campusId, String employeeId);

    /** Mirrors StudentProfileRepository.findByDepartmentId - both are checked before a department is deactivated. */
    List<FacultyProfile> findByDepartmentId(Long departmentId);

    Page<FacultyProfile> findByCampusIdOrderByIdDesc(Long campusId, Pageable pageable);

    Page<FacultyProfile> findByCampusIdAndDepartmentIdOrderByIdDesc(
            Long campusId, Long departmentId, Pageable pageable);

    long countByCampusId(Long campusId);
}
