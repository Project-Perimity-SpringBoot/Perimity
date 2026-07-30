package com.perimity.auth.controller;

import com.perimity.auth.dto.ApiResponse;
import com.perimity.auth.dto.request.*;
import com.perimity.auth.dto.response.AuthResponse;
import com.perimity.auth.dto.response.OtpChallengeResponse;
import com.perimity.auth.dto.response.UserResponse;
import com.perimity.auth.security.CurrentUser;
import com.perimity.auth.security.JwtService;
import com.perimity.auth.service.AuthService;
import com.perimity.auth.service.AuditService;
import com.perimity.auth.service.TokenDenylistService;
import com.perimity.auth.service.UserAccountService;
import com.perimity.auth.entity.enums.AuditAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Login, one-time codes, visitor registration, passwords.
 *
 * These endpoints are public because reaching them is how a caller obtains a
 * token. Every body carries @Valid - without it none of the DTO constraints
 * run, and the failure is silent.
 */
@RestController
@RequestMapping("/api/auth")
@Validated
@Tag(name = "Authentication", description = "Login, one-time codes, passwords")
public class AuthController {

    private final AuthService authService;
    private final UserAccountService accountService;
    private final CurrentUser currentUser;
    private final JwtService jwtService;
    private final TokenDenylistService denylist;
    private final AuditService audit;

    public AuthController(AuthService authService, UserAccountService accountService,
                          CurrentUser currentUser,
                          JwtService jwtService,
                          TokenDenylistService denylist,
                          AuditService audit) {
        this.authService = authService;
        this.accountService = accountService;
        this.currentUser = currentUser;
        this.jwtService = jwtService;
        this.denylist = denylist;
        this.audit = audit;
    }

    @PostMapping("/login")
    @Operation(summary = "Password login. Visitors cannot use this endpoint.")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequestDto dto,
                                           HttpServletRequest request) {
        return ApiResponse.ok("Signed in", authService.login(dto, clientIp(request)));
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Request a code. The response never reveals whether the account exists.")
    public ApiResponse<OtpChallengeResponse> requestOtp(@Valid @RequestBody OtpRequestDto dto,
                                                        HttpServletRequest request) {
        return ApiResponse.ok("If that address is registered, a code has been sent",
                authService.requestOtp(dto, clientIp(request)));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Submit a code and receive a token")
    public ApiResponse<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyDto dto) {
        return ApiResponse.ok("Signed in", authService.verifyOtp(dto));
    }

    @PostMapping("/visitors/register")
    @Operation(summary = "Visitor self-service. OTP only, never a password.")
    public ResponseEntity<ApiResponse<UserResponse>> registerVisitor(
            @Valid @RequestBody VisitorRegistrationDto dto) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registered", accountService.registerVisitor(dto)));
    }

    @PostMapping("/password/change")
    @Operation(summary = "Change your own password. The account comes from your token.")
    public ApiResponse<Void> changePassword(@Valid @RequestBody PasswordChangeDto dto) {
        accountService.changePassword(currentUser.userId(), dto);
        return ApiResponse.ok("Password changed", null);
    }

    @PostMapping("/password/reset-request")
    @Operation(summary = "Ask for a reset link. Always the same response.")
    public ApiResponse<Void> requestReset(@Valid @RequestBody PasswordResetRequestDto dto) {
        accountService.requestPasswordReset(dto);
        return ApiResponse.ok(
                "If that address has an account, a reset link has been sent", null);
    }

    @PostMapping("/password/reset-confirm")
    @Operation(summary = "Complete a reset using the emailed token")
    public ApiResponse<Void> confirmReset(@Valid @RequestBody PasswordResetConfirmDto dto) {
        accountService.confirmPasswordReset(dto);
        return ApiResponse.ok("Password reset", null);
    }


    /**
     * FR-SESS-2. Ends this session and writes the audit row.
     *
     * A JWT cannot be withdrawn once signed, so "logout" here means remembering
     * this one token's id until it would have expired anyway - see
     * TokenDenylistService. Deleting the copy in the browser is not enough: the
     * token would still work from anywhere else it had been captured.
     *
     * Idempotent, and answers 200 even for a token already logged out. Logout
     * that can fail is logout people learn to distrust, and there is nothing a
     * caller could usefully do about the failure.
     */
    @PostMapping("/logout")
    @Operation(summary = "End this session. The presented token stops working immediately.")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                denylist.deny(jwtService.parse(token).getId(), jwtService.expiryInstantOf(token));
            } catch (RuntimeException ex) {
                // Already expired, or malformed. Either way the caller is
                // logged out - there is nothing left to invalidate.
            }
        }

        var actor = currentUser.require();
        audit.record(AuditAction.LOGOUT, actor.userId(), actor.role(), actor.campusId(),
                "user:" + actor.userId(), null);

        return ApiResponse.ok("Signed out", null);
    }

    @GetMapping("/me")
    @Operation(summary = "Who am I? The React shell calls this on load.")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.ok(accountService.getOne(currentUser.userId()));
    }

    /** X-Forwarded-For first: behind CloudFront the remote address is the load balancer. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
