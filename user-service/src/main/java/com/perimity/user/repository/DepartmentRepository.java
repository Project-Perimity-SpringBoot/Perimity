package com.perimity.user.repository;

import com.perimity.user.entity.Department;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** Departments are per-campus data. Never list them without a campus filter. */
    List<Department> findByCampusIdAndActiveTrueOrderByNameAsc(Long campusId);

    List<Department> findByCampusIdOrderByNameAsc(Long campusId);

    Optional<Department> findByCampusIdAndCodeIgnoreCase(Long campusId, String code);

    boolean existsByCampusIdAndCodeIgnoreCase(Long campusId, String code);
}
