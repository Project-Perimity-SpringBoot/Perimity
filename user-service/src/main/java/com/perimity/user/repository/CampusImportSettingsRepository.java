package com.perimity.user.repository;

import com.perimity.user.entity.CampusImportSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampusImportSettingsRepository
        extends JpaRepository<CampusImportSettings, Long> {

    /** One row per campus, enforced by the unique index on campus_id. */
    Optional<CampusImportSettings> findByCampusId(Long campusId);
}
