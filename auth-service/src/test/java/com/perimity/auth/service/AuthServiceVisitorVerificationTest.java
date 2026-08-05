package com.perimity.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.perimity.auth.client.GatepassVisitorClient;
import com.perimity.auth.dto.request.OtpVerifyDto;
import com.perimity.auth.entity.OtpVerification;
import com.perimity.auth.entity.User;
import com.perimity.auth.entity.enums.OtpPurpose;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.repository.OtpVerificationRepository;
import com.perimity.auth.repository.UserRepository;
import com.perimity.auth.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PROPOSAL. auth-service telling gatepass-service that a visitor's email is
 * confirmed.
 *
 * Three things are worth holding still here, and they are the three that make
 * this safe to add to a working sign-in path:
 *
 *   1. it fires for VISITOR_VERIFICATION
 *   2. it does NOT fire for any other purpose
 *   3. a failure in gatepass-service does not fail the sign-in
 *
 * The third is the one that would hurt in production. A visitor who has proved
 * they own their address must get their token whether or not a different
 * service is healthy.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceVisitorVerificationTest {

    private static final String EMAIL = "visitor@example.com";
    private static final String CODE = "123456";

    @Mock private UserRepository userRepository;
    @Mock private OtpVerificationRepository otpRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RateLimiter rateLimiter;
    @Mock private AuditService audit;
    @Mock private LoginAttemptService loginAttempts;
    @Mock private EmailService emailService;
    @Mock private GatepassVisitorClient gatepassVisitorClient;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, otpRepository, passwordEncoder, jwtService,
                rateLimiter, audit, loginAttempts, emailService, gatepassVisitorClient,
                5, 15, 6, 10, 5, 5, 20);

        lenient().when(jwtService.issue(any(User.class))).thenReturn("a.b.c");
        lenient().when(jwtService.expiryOf(anyString())).thenReturn(LocalDateTime.now().plusDays(1));
    }

    /** The same digest AuthService uses. A different one would never match. */
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void givenAValidCodeFor(OtpPurpose purpose) {
        givenAValidCodeFor(purpose, Role.VISITOR);
    }

    private void givenAValidCodeFor(OtpPurpose purpose, Role role) {
        OtpVerification otp = new OtpVerification();
        otp.setEmail(EMAIL);
        otp.setPurpose(purpose);
        otp.setOtpHash(sha256(CODE));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(9));
        otp.setAttempts(0);
        otp.setConsumed(false);

        given(otpRepository
                .findFirstByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        EMAIL, purpose))
                .willReturn(Optional.of(otp));

        User visitor = new User();
        visitor.setId(7L);
        visitor.setEmail(EMAIL);
        visitor.setName("A Visitor");
        visitor.setRole(role);
        visitor.setActive(true);

        given(userRepository.findByEmailIgnoreCaseAndActiveTrue(EMAIL))
                .willReturn(Optional.of(visitor));
    }

    private OtpVerifyDto dto(OtpPurpose purpose) {
        OtpVerifyDto d = new OtpVerifyDto();
        d.setEmail(EMAIL);
        d.setPurpose(purpose);
        d.setCode(CODE);
        return d;
    }

    /**
     * THE PATH THE APP ACTUALLY USES. Every visitor screen - register, request
     * a code, verify it - sends purpose LOGIN. The previous version of this
     * test asserted VISITOR_VERIFICATION, an enum value no client sends, so it
     * passed while the feature could never fire.
     */
    @Test
    void tellsGatepassWhenAVisitorSignsInWithALoginCode() {
        givenAValidCodeFor(OtpPurpose.LOGIN, Role.VISITOR);

        service.verifyOtp(dto(OtpPurpose.LOGIN));

        verify(gatepassVisitorClient).markEmailVerified(EMAIL, 7L);
    }

    @Test
    void tellsGatepassOnAnExplicitVerificationCodeToo() {
        givenAValidCodeFor(OtpPurpose.VISITOR_VERIFICATION, Role.VISITOR);

        service.verifyOtp(dto(OtpPurpose.VISITOR_VERIFICATION));

        verify(gatepassVisitorClient).markEmailVerified(EMAIL, 7L);
    }

    /**
     * Staff never have a visitor request pending. Scoping on role keeps their
     * sign-ins off this path entirely, which is what the purpose gate was
     * trying and failing to achieve.
     */
    @Test
    void staysQuietWhenStaffSignInWithACode() {
        givenAValidCodeFor(OtpPurpose.LOGIN, Role.FACULTY);

        service.verifyOtp(dto(OtpPurpose.LOGIN));

        verify(gatepassVisitorClient, never()).markEmailVerified(anyString(), anyLong());
    }

    @Test
    void staysQuietForAStudent() {
        givenAValidCodeFor(OtpPurpose.LOGIN, Role.STUDENT);

        service.verifyOtp(dto(OtpPurpose.LOGIN));

        verify(gatepassVisitorClient, never()).markEmailVerified(anyString(), anyLong());
    }

    /**
     * The one that matters. gatepass-service being down must not stop a visitor
     * signing in - they have already proved they own the address, and their
     * token has nothing to do with whether a pass was issued.
     */
    @Test
    void signInStillSucceedsWhenGatepassIsUnreachable() {
        givenAValidCodeFor(OtpPurpose.LOGIN, Role.VISITOR);
        given(gatepassVisitorClient.markEmailVerified(anyString(), anyLong()))
                .willReturn(false);

        var response = service.verifyOtp(dto(OtpPurpose.LOGIN));

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("a.b.c");
    }
}
