package com.perimity.auth.repository;

import com.perimity.auth.entity.BlocklistEntry;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlocklistEntryRepository extends JpaRepository<BlocklistEntry, Long> {

    /**
     * The screening check, run on every registration and every bulk-upload row.
     * Scoped by campus - barred at one campus is not barred at another.
     */
    boolean existsByCampusIdAndEmailIgnoreCase(Long campusId, String email);

    boolean existsByCampusIdAndPhone(Long campusId, String phone);

    /** Screen 17 - Blocklist, with search. */
    Page<BlocklistEntry> findByCampusIdOrderByCreatedAtDesc(Long campusId, Pageable pageable);

    Page<BlocklistEntry> findByCampusIdAndEmailContainingIgnoreCase(
            Long campusId, String emailFragment, Pageable pageable);

    Optional<BlocklistEntry> findByIdAndCampusId(Long id, Long campusId);

    long countByCampusId(Long campusId);
}
