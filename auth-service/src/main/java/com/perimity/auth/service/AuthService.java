package com.perimity.auth.service;

import com.perimity.auth.dto.request.LoginRequestDto;
import com.perimity.auth.dto.request.OtpRequestDto;
import com.perimity.auth.dto.request.OtpVerifyDto;
import com.perimity.auth.dto.response.AuthResponse;
import com.perimity.auth.dto.response.OtpChallengeResponse;
import com.perimity.auth.dto.response.UserResponse;
import com.perimity.auth.entity.OtpVerification;
import com.perimity.auth.entity.User;
import com.perimity.auth.exception.AuthenticationFailedException;
import com.perimity.auth.repository.OtpVerificationRepository;
import com.perimity.auth.repository.UserRepository;
import com.perimity.auth.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
 * Login and OTP.
 *
 * The DTOs already proved the input is well formed. Everything here needs the
 * database or the clock.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final int maxFailedAttempts;
    private final int lockoutMinutes;
    private final int otpLength;
    private final int otpExpiryMinutes;
    private final int otpMaxAttempts;
    private final int otpMaxRequests;
    private final int otpRequestWindowMinutes;

    public AuthService(UserRepository userRepository,
                       OtpVerificationRepository otpRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${perimity.password.max-failed-attempts}") int maxFailedAttempts,
                       @Value("${perimity.password.lockout-minutes}") int lockoutMinutes,
                       @Value("${perimity.otp.length}") int otpLength,
                       @Value("${perimity.otp.expiry-minutes}") int otpExpiryMinutes,
                       @Value("${perimity.otp.max-attempts}") int otpMaxAttempts,
                       @Value("${perimity.otp.max-requests}") int otpMaxRequests,
                       @Value("${perimity.otp.request-window-minutes}") int otpRequestWindowMinutes) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutMinutes = lockoutMinutes;
        this.otpLength = otpLength;
        this.otpExpiryMinutes = otpExpiryMinutes;
        this.otpMaxAttempts = otpMaxAttempts;
        this.otpMaxRequests = otpMaxRequests;
        this.otpRequestWindowMinutes = otpRequestWindowMinutes;
    }

    // ------------------------------------------------------------ login

    /**
     * Password login.
     *
     * Every failure path except lockout returns the same message. An unknown
     * email and a wrong password must be indistinguishable, or the endpoint
     * tells an attacker which addresses are registered.
     */
    @Transactional
    public AuthResponse login(LoginRequestDto dto) {
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findByEmailIgnoreCase(dto.getEmail())
                .orElseThrow(AuthenticationFailedException::invalidCredentials);

        if (!user.isActive()) {
            throw AuthenticationFailedException.invalidCredentials();
        }

        // A visitor has no password at all, so this endpoint is not for them.
        // Same generic message - do not reveal that the account exists as a visitor.
        if (!user.getRole().canLoginWithPassword() || user.getPasswordHash() == null) {
            throw AuthenticationFailedException.invalidCredentials();
        }

        if (user.isLockedAt(now)) {
            throw new AuthenticationFailedException(
                    "This account is temporarily locked. Try again after "
                            + user.getLockedUntil().toLocalTime().withNano(0) + ".");
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            registerFailedAttempt(user, now);
            throw AuthenticationFailedException.invalidCredentials();
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);

        return tokenFor(user);
    }

    /** Counts the miss and locks the account once the budget is spent. */
    private void registerFailedAttempt(User user, LocalDateTime now) {
        int failures = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(failures);
        if (failures >= maxFailedAttempts) {
            user.setLockedUntil(now.plusMinutes(lockoutMinutes));
            user.setFailedLoginCount(0);
        }
        userRepository.save(user);
    }

    // -------------------------------------------------------------- otp

    /**
     * Issue a one-time code.
     *
     * Returns the SAME response whether or not the email belongs to an account.
     * A different answer for an unknown address makes this an enumeration tool.
     *
     * Rate limited per email using the existing repository count. The per-IP
     * limit lands tomorrow with Redis - this one alone does not stop a bot
     * cycling through addresses.
     */
    @Transactional
    public OtpChallengeResponse requestOtp(OtpRequestDto dto) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusMinutes(otpRequestWindowMinutes);

        long recent = otpRepository.countByEmailIgnoreCaseAndCreatedAtAfter(dto.getEmail(), windowStart);
        if (recent >= otpMaxRequests) {
            throw new IllegalStateException(
                    "Too many codes requested. Please wait " + otpRequestWindowMinutes
                            + " minutes before trying again.");
        }

        // Any older unused code for this email stops working the moment a new
        // one is issued, so two live codes never exist at once.
        otpRepository.consumeOutstanding(dto.getEmail(), dto.getPurpose(), now);

        String plainCode = generateNumericCode(otpLength);
        LocalDateTime expiresAt = now.plusMinutes(otpExpiryMinutes);

        otpRepository.save(OtpVerification.builder()
                .email(dto.getEmail().toLowerCase())
                .otpHash(sha256(plainCode))
                .purpose(dto.getPurpose())
                .campusId(dto.getCampusId())
                .expiresAt(expiresAt)
                .attempts(0)
                .consumed(false)
                .build());

        // Day 9 replaces this with SES. Until then the code is logged so the
        // team can test. This log line MUST be deleted before deployment.
        log.warn("DEV ONLY - OTP for {} ({}) is {}", dto.getEmail(), dto.getPurpose(), plainCode);

        return OtpChallengeResponse.of(dto.getEmail(), dto.getPurpose(), expiresAt, otpMaxAttempts);
    }

    /**
     * Verify a code and, if it belongs to a real account, issue a token.
     *
     * A wrong code burns an attempt. Once the attempt budget is gone the code
     * is dead even if the right one is typed next - otherwise a six-digit code
     * is brute-forceable in about a minute.
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
                    "That code has expired or has been tried too many times. Please request a new one.");
        }

        if (!constantTimeEquals(sha256(dto.getCode()), otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            throw new AuthenticationFailedException("That code is not correct.");
        }

        otp.setConsumed(true);
        otp.setConsumedAt(now);
        otpRepository.save(otp);

        Optional<User> maybeUser = userRepository.findByEmailIgnoreCaseAndActiveTrue(dto.getEmail());
        if (maybeUser.isEmpty()) {
            // The code was right but no account exists yet. Registration is a
            // separate endpoint - this one does not create accounts.
            throw new AuthenticationFailedException(
                    "Verified, but there is no active account for this email yet.");
        }

        User user = maybeUser.get();
        user.setLastLoginAt(now);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        return tokenFor(user);
    }

    // ----------------------------------------------------------- helpers

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

    private String sha256(String value) {
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
