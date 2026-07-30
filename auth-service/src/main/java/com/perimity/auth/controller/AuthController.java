package com.perimity.auth.controller;

import com.perimity.auth.dto.ApiResponse;
import com.perimity.auth.dto.request.*;
import com.perimity.auth.dto.response.AuthResponse;
import com.perimity.auth.dto.response.OtpChallengeResponse;
import com.perimity.auth.dto.response.UserResponse;
import com.perimity.auth.security.CurrentUser;
import com.perimity.auth.service.AuthService;
import com.perimity.auth.service.UserAccountService;
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

    public AuthController(AuthService authService, UserAccountService accountService,
                          CurrentUser currentUser) {
        this.authService = authService;
        this.accountService = accountService;
        this.currentUser = currentUser;
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
