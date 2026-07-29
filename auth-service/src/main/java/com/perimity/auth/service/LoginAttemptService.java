package com.perimity.auth.service;

import com.perimity.auth.entity.User;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a failed login and locks the account when the budget runs out.
 *
 * ===================================================================
 *  WHY THIS IS A SEPARATE CLASS - do not fold it back into AuthService
 * ===================================================================
 *
 * The first version incremented the counter inside AuthService.login() and then
 * threw AuthenticationFailedException. login() is @Transactional, and Spring
 * rolls a transaction back on any RuntimeException - so the increment was
 * rolled back by the very exception that followed it.
 *
 * The effect: the counter stayed at 0 forever and the account could NEVER lock.
 * Verified before the fix - six wrong passwords left failed_login_count = 0 and
 * locked_until = NULL. Unlimited password guessing, and nothing in the code
 * looked wrong.
 *
 * REQUIRES_NEW puts the increment in its own transaction, which commits
 * independently of the failure that follows. It has to be a separate bean
 * because Spring's proxying ignores propagation on a self-invoked method.
 */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AuditService audit;
    private final int maxFailedAttempts;
    private final int lockoutMinutes;

    public LoginAttemptService(UserRepository userRepository,
                               AuditService audit,
                               @Value("${perimity.password.max-failed-attempts}") int maxFailedAttempts,
                               @Value("${perimity.password.lockout-minutes}") int lockoutMinutes) {
        this.userRepository = userRepository;
        this.audit = audit;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutMinutes = lockoutMinutes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int failures = user.getFailedLoginCount() + 1;

        if (failures >= maxFailedAttempts) {
            user.setLockedUntil(now.plusMinutes(lockoutMinutes));
            user.setFailedLoginCount(0);
            userRepository.save(user);

            audit.record(AuditAction.ACCOUNT_LOCKED, user.getId(), user.getRole(),
                    user.getCampusId(), "user:" + user.getId(),
                    "Locked for " + lockoutMinutes + " minutes after "
                            + maxFailedAttempts + " failed attempts");
        } else {
            user.setFailedLoginCount(failures);
            userRepository.save(user);

            audit.record(AuditAction.LOGIN_FAILED, user.getId(), user.getRole(),
                    user.getCampusId(), "user:" + user.getId(),
                    "Attempt " + failures + " of " + maxFailedAttempts);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }
}
