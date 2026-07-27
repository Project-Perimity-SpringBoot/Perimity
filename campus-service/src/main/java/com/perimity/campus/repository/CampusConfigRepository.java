package com.perimity.campus.repository;

import com.perimity.campus.entity.CampusConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampusConfigRepository extends JpaRepository<CampusConfig, Long> {

    /** Read the whole rule set for a campus in one go, e.g. to cache it. */
    List<CampusConfig> findByCampusId(Long campusId);

    /** Read one specific rule, e.g. "approval.required" for a campus. */
    Optional<CampusConfig> findByCampusIdAndConfigKey(Long campusId, String configKey);

    boolean existsByCampusIdAndConfigKey(Long campusId, String configKey);
}
