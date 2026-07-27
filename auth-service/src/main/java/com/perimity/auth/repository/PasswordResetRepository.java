package com.perimity.auth.repository;

import com.perimity.auth.entity.PasswordReset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

    Optional<PasswordReset> findByTokenHashAndUsedFalse(String tokenHash);

    Optional<PasswordReset> findByTokenHash(String tokenHash);

    List<PasswordReset> findByUserIdAndUsedFalse(Long userId);

    int deleteByExpiresAtBefore(LocalDateTime cutoff);
}
