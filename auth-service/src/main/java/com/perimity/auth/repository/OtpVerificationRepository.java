package com.perimity.auth.repository;

import com.perimity.auth.entity.OtpVerification;
import com.perimity.auth.entity.enums.OtpPurpose;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    /** The verify step: newest unconsumed OTP for this email and purpose. */
    Optional<OtpVerification> findFirstByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
            String email, OtpPurpose purpose);

    /**
     * Rate limiting (FR-REG-7): how many OTPs this email has requested inside
     * the window. Compare against OTP_MAX_REQUESTS from .env.
     */
    long countByEmailIgnoreCaseAndCreatedAtAfter(String email, LocalDateTime since);

    List<OtpVerification> findByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    /**
     * Invalidate any outstanding OTP before issuing a new one, so an older code
     * cannot be used after a fresh one is sent.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE OtpVerification o
               SET o.consumed = true, o.consumedAt = :now
             WHERE LOWER(o.email) = LOWER(:email)
               AND o.purpose = :purpose
               AND o.consumed = false
            """)
    int consumeOutstanding(@Param("email") String email,
                           @Param("purpose") OtpPurpose purpose,
                           @Param("now") LocalDateTime now);

    /** Housekeeping: clear codes that expired long ago. */
    @Modifying
    @Transactional
    int deleteByExpiresAtBefore(LocalDateTime cutoff);
}
