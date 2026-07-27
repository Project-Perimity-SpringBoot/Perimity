package com.perimity.user.repository;

import com.perimity.user.entity.FacultyProfile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FacultyProfileRepository extends JpaRepository<FacultyProfile, Long> {

    Optional<FacultyProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Optional<FacultyProfile> findByCampusIdAndEmployeeIdIgnoreCase(Long campusId, String employeeId);

    Page<FacultyProfile> findByCampusIdOrderByIdDesc(Long campusId, Pageable pageable);

    long countByCampusId(Long campusId);
}
