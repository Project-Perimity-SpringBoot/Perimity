package com.perimity.qr.repository;

import com.perimity.qr.entity.QrRecord;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QrRecordRepository extends JpaRepository<QrRecord, Long> {

    /** The gate scan lookup. The guard sends a token; we hash it and match here. */
    Optional<QrRecord> findByTokenHashAndActiveTrue(String tokenHash);

    Optional<QrRecord> findByTokenHash(String tokenHash);

    /** The currently valid QR for a pass. Older rows stay for audit. */
    Optional<QrRecord> findByPassIdAndActiveTrue(Long passId);

    /**
     * DAY 10. Same lookup, but holding a row lock until the transaction ends.
     *
     * Needed the moment listener concurrency went above 1. Two messages for the
     * same pass - a re-issue arriving while the original is still generating -
     * previously ran generate() in parallel: both read "no active record" or
     * both read the same one, both retired it, both inserted. The result is
     * either two active QRs for one pass or none, and neither is visible until
     * a guard scans a token that should work and gets a red screen.
     *
     * PESSIMISTIC_WRITE issues SELECT ... FOR UPDATE, so the second transaction
     * blocks until the first commits and then sees its result. Optimistic
     * locking would not help here: there is no version conflict to detect when
     * the second thread's problem is that it read the row too early.
     *
     * NOTE this locks an EXISTING row only. Two concurrent first-ever
     * generations for the same pass lock nothing, because there is no row to
     * lock. That gap is closed in the database by the partial unique index in
     * db/migration/V2__day10_concurrency.sql - application code cannot enforce
     * an invariant it has not yet written a row for.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from QrRecord r where r.passId = :passId and r.active = true")
    Optional<QrRecord> findActiveByPassIdForUpdate(@Param("passId") Long passId);

    /** Every QR ever issued for a pass, newest first - used on re-issue. */
    List<QrRecord> findByPassIdOrderByCreatedAtDesc(Long passId);

    boolean existsByTokenHash(String tokenHash);

    long countByCampusIdAndActiveTrue(Long campusId);
}
