package com.perimity.auth.service;

import com.perimity.auth.dto.request.InternalIdentityCreateDto;
import com.perimity.auth.dto.request.PasswordChangeDto;
import com.perimity.auth.dto.request.PasswordResetConfirmDto;
import com.perimity.auth.dto.request.PasswordResetRequestDto;
import com.perimity.auth.dto.request.UserCreateDto;
import com.perimity.auth.dto.request.UserStatusUpdateDto;
import com.perimity.auth.dto.request.UserUpdateDto;
import com.perimity.auth.dto.request.VisitorRegistrationDto;
import com.perimity.auth.dto.response.PageResponse;
import com.perimity.auth.dto.response.UserResponse;
import com.perimity.auth.entity.PasswordReset;
import com.perimity.auth.entity.User;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.exception.AuthenticationFailedException;
import com.perimity.auth.exception.RateLimitedException;
import com.perimity.auth.exception.ResourceNotFoundException;
import com.perimity.auth.repository.BlocklistEntryRepository;
import com.perimity.auth.repository.PasswordResetRepository;
import com.perimity.auth.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts and passwords.
 *
 * Nothing is ever hard-deleted. Accounts are deactivated, so the audit trail
 * and every pass they ever held stay meaningful.
 */
@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetRepository resetRepository;
    private final BlocklistEntryRepository blocklistRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final EmailService emailService;
    private final int resetExpiryMinutes;
    private final int resetPerEmailPerDay;
    private final String resetUrlBase;

    public UserAccountService(UserRepository userRepository,
                              PasswordResetRepository resetRepository,
                              BlocklistEntryRepository blocklistRepository,
                              PasswordEncoder passwordEncoder,
                              RateLimiter rateLimiter,
                              AuditService audit,
                              EmailService emailService,
                              @Value("${perimity.password.reset-link-expiry-minutes}") int resetExpiryMinutes,
                              @Value("${perimity.ratelimit.reset.per-email-per-day}") int resetPerEmailPerDay,
                              @Value("${perimity.frontend.reset-password-url}") String resetUrlBase) {
        this.userRepository = userRepository;
        this.resetRepository = resetRepository;
        this.blocklistRepository = blocklistRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.emailService = emailService;
        this.resetExpiryMinutes = resetExpiryMinutes;
        this.resetPerEmailPerDay = resetPerEmailPerDay;
        this.resetUrlBase = resetUrlBase;
    }

    // --------------------------------------------------------- accounts

    /** An admin creates a Faculty, Guard, Student or Campus Admin account. */
    @Transactional
    public UserResponse create(UserCreateDto dto, Long actorUserId, Role actorRole) {
        if (userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            throw new IllegalArgumentException("An account already exists for that email.");
        }

        String hash = dto.getRole().canLoginWithPassword()
                ? passwordEncoder.encode(dto.getTemporaryPassword())
                : null;

        User user = userRepository.save(User.builder()
                .email(dto.getEmail().toLowerCase())
                .name(dto.getName().trim())
                .phone(dto.getPhone())
                .role(dto.getRole())
                .campusId(dto.getCampusId())
                .passwordHash(hash)
                // An admin-set password is temporary by definition. The holder
                // must replace it before doing anything else.
                .mustChangePassword(hash != null)
                .active(true)
                .build());

        audit.record(AuditAction.ACCOUNT_CREATED, actorUserId, actorRole,
                user.getCampusId(), "user:" + user.getId(),
                "Created " + user.getRole() + " account");

        return UserResponse.from(user);
    }

    /**
     * Visitor self-service (SRS v1.1).
     *
     * Blocklist checked here. Per FR-BLK-4 the refusal is deliberately vague -
     * telling someone they are blocklisted tells them which identity to avoid
     * using next.
     */
    @Transactional
    public UserResponse registerVisitor(VisitorRegistrationDto dto) {
        boolean blocked = blocklistRepository
                        .existsByCampusIdAndEmailIgnoreCase(dto.getCampusId(), dto.getEmail())
                || (dto.getPhone() != null
                        && blocklistRepository.existsByCampusIdAndPhone(dto.getCampusId(), dto.getPhone()));

        if (blocked) {
            audit.recordAnonymous(AuditAction.BLOCKED_REGISTRATION_ATTEMPT,
                    "email:" + dto.getEmail(), "Campus " + dto.getCampusId());
            throw new IllegalStateException(
                    "We could not complete your registration. Please contact the campus office.");
        }

        Optional<User> existing = userRepository.findByEmailIgnoreCase(dto.getEmail());
        if (existing.isPresent()) {
            // A pass is not a person. Somebody who visited last year already has
            // an identity, and reusing it is the whole point of keying on email.
            return UserResponse.from(existing.get());
        }

        User user = userRepository.save(User.builder()
                .email(dto.getEmail().toLowerCase())
                .name(dto.getName().trim())
                .phone(dto.getPhone())
                .role(Role.VISITOR)
                .campusId(dto.getCampusId())
                .passwordHash(null)
                .mustChangePassword(false)
                .active(true)
                .build());

        audit.recordAnonymous(AuditAction.ACCOUNT_CREATED,
                "user:" + user.getId(), "Visitor self-registration");

        return UserResponse.from(user);
    }

    // ------------------------------------------------- internal (Day 8)

    /**
     * The internal lookup other services need.
     *
     * GET /api/internal/auth/users/by-email is a read-only check: "does an
     * identity already exist for this email, and if so what is it." This is
     * what the bulk engine calls per row before deciding whether to reuse an
     * identity or create a new lightweight visitor (Event_Bulk_Design.md,
     * "The Mixed-Attendee Problem"). No audit row here - a lookup that finds
     * nothing has changed nothing.
     */
    @Transactional(readOnly = true)
    public Optional<UserResponse> findByEmailForInternal(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(UserResponse::from);
    }

    /**
     * POST /api/internal/auth/users.
     *
     * Same resolve-or-create shape as registerVisitor, called by a service
     * instead of by a visitor. Idempotent by design: the bulk engine may call
     * this twice for the same row on a retry, and a second call must return
     * the same identity, not a duplicate account or an error.
     */
    @Transactional
    public IdentityResolution resolveOrCreateInternalIdentity(InternalIdentityCreateDto dto) {
        boolean blocked = blocklistRepository
                        .existsByCampusIdAndEmailIgnoreCase(dto.getCampusId(), dto.getEmail())
                || (dto.getPhone() != null
                        && blocklistRepository.existsByCampusIdAndPhone(dto.getCampusId(), dto.getPhone()));

        if (blocked) {
            audit.recordAnonymous(AuditAction.BLOCKED_REGISTRATION_ATTEMPT,
                    "email:" + dto.getEmail(),
                    "Blocked during internal identity resolution, campus " + dto.getCampusId()
                            + (dto.getSource() == null ? "" : ", source " + dto.getSource()));
            throw new IllegalStateException("This person cannot be registered for that campus.");
        }

        Optional<User> existing = userRepository.findByEmailIgnoreCase(dto.getEmail());
        if (existing.isPresent()) {
            return new IdentityResolution(UserResponse.from(existing.get()), false);
        }

        User user = userRepository.save(User.builder()
                .email(dto.getEmail().toLowerCase())
                .name(dto.getName().trim())
                .phone(dto.getPhone())
                .role(Role.VISITOR)
                .campusId(dto.getCampusId())
                .passwordHash(null)
                .mustChangePassword(false)
                .active(true)
                .build());

        audit.recordAnonymous(AuditAction.ACCOUNT_CREATED, "user:" + user.getId(),
                "Created via internal service call"
                        + (dto.getSource() == null ? "" : ", source " + dto.getSource()));

        return new IdentityResolution(UserResponse.from(user), true);
    }

    /** created tells the controller whether to answer 200 (reused) or 201 (new). */
    public record IdentityResolution(UserResponse user, boolean created) { }

    // ------------------------------------------------------------ reads

    @Transactional
    public UserResponse update(Long id, UserUpdateDto dto) {
        User user = require(id);
        user.setName(dto.getName().trim());
        user.setPhone(dto.getPhone());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse changeStatus(Long id, UserStatusUpdateDto dto, Long actorUserId, Role actorRole) {
        User user = require(id);

        if (user.isActive() == dto.getActive()) {
            throw new IllegalStateException("This account is already "
                    + (user.isActive() ? "active" : "inactive") + ".");
        }

        user.setActive(dto.getActive());
        if (!dto.getActive()) {
            // Deactivating clears the lock so reactivation is not blocked by a
            // stale lockout from months ago.
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
        }
        userRepository.save(user);

        audit.record(dto.getActive() ? AuditAction.ACCOUNT_CREATED : AuditAction.ACCOUNT_DEACTIVATED,
                actorUserId, actorRole, user.getCampusId(), "user:" + user.getId(),
                dto.getReason());

        // TODO Day 8: when deactivating a holder, call gatepass-service
        //   POST /internal/passes/holder/{id}/pause
        // so their live passes stop opening gates immediately.
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getOne(Long id) {
        return UserResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> byCampus(Long campusId, Role role, Pageable pageable) {
        return PageResponse.from(
                role == null
                        ? userRepository.findByCampusIdOrderByNameAsc(campusId, pageable)
                        : userRepository.findByCampusIdAndRoleOrderByNameAsc(campusId, role, pageable),
                UserResponse::from);
    }

    // -------------------------------------------------------- passwords

    /** A signed-in user changes their own. The account comes from the token. */
    @Transactional
    public void changePassword(Long userId, PasswordChangeDto dto) {
        User user = require(userId);

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Your current password is not correct.");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Any outstanding reset link is now void. Otherwise an old emailed link
        // could undo a password the owner just set.
        resetRepository.findByUserIdAndUsedFalse(userId).forEach(r -> {
            r.setUsed(true);
            r.setUsedAt(LocalDateTime.now());
            resetRepository.save(r);
        });

        audit.record(AuditAction.PASSWORD_CHANGED, userId, user.getRole(),
                user.getCampusId(), "user:" + userId, null);
    }

    /**
     * Request a reset link.
     *
     * ALWAYS returns quietly, whether or not the address exists. Anything else
     * makes this an account-enumeration tool.
     *
     * Day 9: the link is now emailed instead of logged. The Optional<String>
     * return is kept as-is - AuthController already ignores it - but nothing
     * outside EmailService sees the plain token any more.
     */
    @Transactional
    public Optional<String> requestPasswordReset(PasswordResetRequestDto dto) {
        if (!rateLimiter.allow("reset-email", dto.getEmail(), resetPerEmailPerDay, Duration.ofDays(1))) {
            throw new RateLimitedException("Too many reset requests for this address.",
                    rateLimiter.secondsUntilReset("reset-email", dto.getEmail()));
        }

        Optional<User> maybeUser = userRepository.findByEmailIgnoreCaseAndActiveTrue(dto.getEmail());
        if (maybeUser.isEmpty() || !maybeUser.get().getRole().canLoginWithPassword()) {
            audit.recordAnonymous(AuditAction.PASSWORD_RESET_REQUESTED,
                    "email:" + dto.getEmail(), "No eligible account - no link sent");
            return Optional.empty();
        }

        User user = maybeUser.get();

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String plainToken = AuthService.sha256(HexFormat.of().formatHex(raw));

        resetRepository.save(PasswordReset.builder()
                .userId(user.getId())
                .tokenHash(AuthService.sha256(plainToken))
                .expiresAt(LocalDateTime.now().plusMinutes(resetExpiryMinutes))
                .build());

        audit.record(AuditAction.PASSWORD_RESET_REQUESTED, user.getId(), user.getRole(),
                user.getCampusId(), "user:" + user.getId(), null);

        String resetLink = resetUrlBase + "?token=" + plainToken;
        emailService.sendPasswordResetLink(dto.getEmail(), resetLink, resetExpiryMinutes);

        return Optional.of(plainToken);
    }

    /** Complete a reset. The token is looked up by its hash - only the hash is stored. */
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmDto dto) {
        PasswordReset reset = resetRepository
                .findByTokenHashAndUsedFalse(AuthService.sha256(dto.getToken()))
                .orElseThrow(() -> new AuthenticationFailedException(
                        "That reset link is not valid. Please request a new one."));

        if (!reset.isUsableAt(LocalDateTime.now())) {
            throw new AuthenticationFailedException(
                    "That reset link has expired. Please request a new one.");
        }

        User user = require(reset.getUserId());
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setMustChangePassword(false);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        reset.setUsed(true);
        reset.setUsedAt(LocalDateTime.now());
        resetRepository.save(reset);

        audit.record(AuditAction.PASSWORD_CHANGED, user.getId(), user.getRole(),
                user.getCampusId(), "user:" + user.getId(), "Completed via reset link");
    }

    private User require(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", id));
    }
}
