package com.perimity.auth.service;

import com.perimity.auth.dto.request.LoginRequestDto;
import com.perimity.auth.dto.request.OtpRequestDto;
import com.perimity.auth.dto.request.OtpVerifyDto;
import com.perimity.auth.dto.response.AuthResponse;
import com.perimity.auth.dto.response.OtpChallengeResponse;
import com.perimity.auth.dto.response.UserResponse;
import com.perimity.auth.entity.OtpVerification;
import com.perimity.auth.entity.User;
import com.perimity.auth.entity.enums.AuditAction;
import com.perimity.auth.exception.AuthenticationFailedException;
import com.perimity.auth.exception.RateLimitedException;
import com.perimity.auth.repository.OtpVerificationRepository;
import com.perimity.auth.repository.UserRepository;
import com.perimity.auth.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login and one-time codes.
 *
 * The DTOs already proved the input is well formed. Everything here needs the
 * database, Redis or the clock.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final LoginAttemptService loginAttempts;

    private final int maxFailedAttempts;
    private final int lockoutMinutes;
    private final int otpLength;
    private final int otpExpiryMinutes;
    private final int otpMaxAttempts;
    private final int otpPerEmailPerHour;
    private final int otpPerIpPerHour;

    public AuthService(UserRepository userRepository,
                       OtpVerificationRepository otpRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RateLimiter rateLimiter,
                       AuditService audit,
                       LoginAttemptService loginAttempts,
                       @Value("${perimity.password.max-failed-attempts}") int maxFailedAttempts,
                       @Value("${perimity.password.lockout-minutes}") int lockoutMinutes,
                       @Value("${perimity.otp.length}") int otpLength,
                       @Value("${perimity.otp.expiry-minutes}") int otpExpiryMinutes,
                       @Value("${perimity.otp.max-attempts}") int otpMaxAttempts,
                       @Value("${perimity.ratelimit.otp.per-email-per-hour}") int otpPerEmailPerHour,
                       @Value("${perimity.ratelimit.otp.per-ip-per-hour}") int otpPerIpPerHour) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.loginAttempts = loginAttempts;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutMinutes = lockoutMinutes;
        this.otpLength = otpLength;
        this.otpExpiryMinutes = otpExpiryMinutes;
        this.otpMaxAttempts = otpMaxAttempts;
        this.otpPerEmailPerHour = otpPerEmailPerHour;
        this.otpPerIpPerHour = otpPerIpPerHour;
    }

    // ------------------------------------------------------------ login

    /**
     * Password login.
     *
     * Every failure except lockout returns the same message. An unknown email
     * and a wrong password must be indistinguishable.
     */
    @Transactional
    public AuthResponse login(LoginRequestDto dto, String clientIp) {
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findByEmailIgnoreCase(dto.getEmail())
                .orElseThrow(() -> {
                    audit.recordAnonymous(AuditAction.LOGIN_FAILED,
                            "user:" + dto.getEmail(), "No such account");
                    return AuthenticationFailedException.invalidCredentials();
                });

        if (!user.isActive()) {
            audit.record(AuditAction.LOGIN_FAILED, user.getId(), user.getRole(),
                    user.getCampusId(), "user:" + user.getId(), "Account is inactive");
            throw AuthenticationFailedException.invalidCredentials();
        }

        // A visitor has no password at all. Same generic refusal - saying
        // "visitors cannot log in here" would confirm the account exists.
        if (!user.getRole().canLoginWithPassword() || user.getPasswordHash() == null) {
            throw AuthenticationFailedException.invalidCredentials();
        }

        if (user.isLockedAt(now)) {
            throw new AuthenticationFailedException(
                    "This account is temporarily locked. Try again after "
                            + user.getLockedUntil().toLocalTime().withNano(0) + ".");
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            // Separate transaction. See LoginAttemptService for why - doing this
            // inline meant the increment was rolled back by the exception below,
            // and the account could never lock.
            loginAttempts.recordFailure(user.getId());
            throw AuthenticationFailedException.invalidCredentials();
        }

        loginAttempts.recordSuccess(user.getId());

        rateLimiter.reset("login-ip", clientIp);
        audit.record(AuditAction.LOGIN_SUCCESS, user.getId(), user.getRole(),
                user.getCampusId(), "user:" + user.getId(), null);

        return tokenFor(user);
    }

    // -------------------------------------------------------------- otp

    /**
     * Issue a one-time code.
     *
     * Returns the SAME response whether or not the email belongs to an account.
     * Rate limited on both the email and the caller's IP - the email limit
     * alone does not stop a bot cycling through a thousand addresses.
     */
    @Transactional
    public OtpChallengeResponse requestOtp(OtpRequestDto dto, String clientIp) {
        enforce("otp-email", dto.getEmail(), otpPerEmailPerHour, Duration.ofHours(1),
                "Too many codes requested for this address.");
        enforce("otp-ip", clientIp, otpPerIpPerHour, Duration.ofHours(1),
                "Too many codes requested from this device.");

        LocalDateTime now = LocalDateTime.now();

        // Any older unused code stops working the moment a new one is issued,
        // so two live codes never exist for the same email and purpose.
        otpRepository.consumeOutstanding(dto.getEmail(), dto.getPurpose(), now);

        String plainCode = generateNumericCode(otpLength);
        LocalDateTime expiresAt = now.plusMinutes(otpExpiryMinutes);

        otpRepository.save(OtpVerification.builder()
                .email(dto.getEmail().toLowerCase())
                .otpHash(sha256(plainCode))
                .purpose(dto.getPurpose())
                .campusId(dto.getCampusId())
                .expiresAt(expiresAt)
                .build());

        audit.recordAnonymous(AuditAction.OTP_REQUESTED,
                "email:" + dto.getEmail(), "Purpose " + dto.getPurpose());

        // Day 9 replaces this with SES. DELETE THIS LINE BEFORE DEPLOYMENT.
        log.warn("DEV ONLY - OTP for {} ({}) is {}", dto.getEmail(), dto.getPurpose(), plainCode);

        return OtpChallengeResponse.of(dto.getEmail(), dto.getPurpose(), expiresAt, otpMaxAttempts);
    }

    /**
     * Verify a code and issue a token.
     *
     * A wrong code burns an attempt. Once the budget is gone the code is dead
     * even if the right one is typed next - a six-digit code is otherwise
     * brute-forceable in about a minute.
     */
    @Transactional
    public AuthResponse verifyOtp(OtpVerifyDto dto) {
        LocalDateTime now = LocalDateTime.now();

        OtpVerification otp = otpRepository
                .findFirstByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        dto.getEmail(), dto.getPurpose())
                .orElseThrow(() -> new AuthenticationFailedException(
                        "That code is not valid. Please request a new one."));

        if (!otp.isUsableAt(now, otpMaxAttempts)) {
            throw new AuthenticationFailedException(
                    "That code has expired or has been tried too many times. Request a new one.");
        }

        if (!constantTimeEquals(sha256(dto.getCode()), otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            audit.recordAnonymous(AuditAction.OTP_FAILED, "email:" + dto.getEmail(),
                    "Attempt " + otp.getAttempts() + " of " + otpMaxAttempts);
            throw new AuthenticationFailedException("That code is not correct.");
        }

        otp.setConsumed(true);
        otp.setConsumedAt(now);
        otpRepository.save(otp);

        Optional<User> maybeUser = userRepository.findByEmailIgnoreCaseAndActiveTrue(dto.getEmail());
        if (maybeUser.isEmpty()) {
            throw new AuthenticationFailedException(
                    "Verified, but there is no active account for this email yet.");
        }

        User user = maybeUser.get();
        user.setLastLoginAt(now);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        audit.record(AuditAction.LOGIN_SUCCESS, user.getId(), user.getRole(),
                user.getCampusId(), "user:" + user.getId(), "Signed in with a one-time code");

        return tokenFor(user);
    }

    // ---------------------------------------------------------- helpers

    private void enforce(String bucket, String subject, int limit, Duration window, String message) {
        if (!rateLimiter.allow(bucket, subject, limit, window)) {
            throw new RateLimitedException(message, rateLimiter.secondsUntilReset(bucket, subject));
        }
    }

    private AuthResponse tokenFor(User user) {
        String token = jwtService.issue(user);
        return AuthResponse.of(token, jwtService.expiryOf(token), UserResponse.from(user));
    }

    /** SecureRandom, not Math.random. A guessable OTP is not an OTP. */
    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** Avoids leaking information through how long the comparison takes. */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
