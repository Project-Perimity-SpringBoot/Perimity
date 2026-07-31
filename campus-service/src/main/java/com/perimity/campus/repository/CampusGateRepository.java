package com.perimity.campus.repository;

import com.perimity.campus.entity.CampusGate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampusGateRepository extends JpaRepository<CampusGate, Long> {

    /** Gates are per-campus. Never list them without a campus filter. */
    List<CampusGate> findByCampusIdAndActiveTrueOrderByNameAsc(Long campusId);

    List<CampusGate> findByCampusIdOrderByNameAsc(Long campusId);

    Optional<CampusGate> findByCampusIdAndNameIgnoreCase(Long campusId, String name);

    boolean existsByCampusIdAndNameIgnoreCase(Long campusId, String name);

    long countByCampusIdAndActiveTrue(Long campusId);
}
