package com.perimity.auth.controller;

import com.perimity.auth.dto.ApiResponse;
import com.perimity.auth.dto.request.LoginRequestDto;
import com.perimity.auth.dto.request.OtpRequestDto;
import com.perimity.auth.dto.request.OtpVerifyDto;
import com.perimity.auth.dto.response.AuthResponse;
import com.perimity.auth.dto.response.OtpChallengeResponse;
import com.perimity.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and one-time codes.
 *
 * All three endpoints are public - they have to be, since reaching them is how
 * a caller obtains a token. They are listed explicitly in SecurityConfig rather
 * than by a wildcard, so adding a new endpoint under /api/auth does not become
 * public by accident.
 *
 * Every body carries @Valid. Without it none of the DTO constraints run, and
 * the failure is silent.
 */
@RestController
@RequestMapping("/api/auth")
@Validated
@Tag(name = "Authentication", description = "Login, one-time codes, token issue")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Password login. Visitors cannot use this endpoint.")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequestDto dto) {
        return ApiResponse.ok("Signed in", authService.login(dto));
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Request a one-time code. The response never reveals whether the account exists.")
    public ApiResponse<OtpChallengeResponse> requestOtp(@Valid @RequestBody OtpRequestDto dto) {
        return ApiResponse.ok("If that address is registered, a code has been sent",
                authService.requestOtp(dto));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Submit a one-time code and receive a token")
    public ApiResponse<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyDto dto) {
        return ApiResponse.ok("Signed in", authService.verifyOtp(dto));
    }
}
