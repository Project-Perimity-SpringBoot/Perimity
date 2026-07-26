package com.perimity.qr.repository;

import com.perimity.qr.entity.QrRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QrRecordRepository extends JpaRepository<QrRecord, Long> {

    /** The gate scan lookup. The guard sends a token; we hash it and match here. */
    Optional<QrRecord> findByTokenHashAndActiveTrue(String tokenHash);

    Optional<QrRecord> findByTokenHash(String tokenHash);

    /** The currently valid QR for a pass. Older rows stay for audit. */
    Optional<QrRecord> findByPassIdAndActiveTrue(Long passId);

    /** Every QR ever issued for a pass, newest first - used on re-issue. */
    List<QrRecord> findByPassIdOrderByCreatedAtDesc(Long passId);

    boolean existsByTokenHash(String tokenHash);

    long countByCampusIdAndActiveTrue(Long campusId);
}
