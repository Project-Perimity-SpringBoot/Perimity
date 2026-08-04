package com.perimity.gatepass.controller;

import com.perimity.gatepass.dto.ApiResponse;
import com.perimity.gatepass.dto.request.VisitorEmailVerifiedDto;
import com.perimity.gatepass.dto.response.VisitorRequestResponse;
import com.perimity.gatepass.service.VisitorRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only. Kept on a separate /internal path so it is obvious
 * at a glance which endpoints must never be reachable from a browser.
 *
 * SECURITY - Day 7, do not skip: this endpoint marks a visitor's email as
 * verified. If a visitor can call it, they verify themselves and the OTP step
 * becomes decorative. It must sit behind the shared internal API key, and
 * /api/gatepass/internal/** must NOT be in the public permit list.
 */
@RestController
@RequestMapping("/api/gatepass/internal/visitor-requests")
@Validated
@Tag(name = "Internal", description = "Service-to-service only. Not for browsers.")
public class VisitorRequestInternalController {

    private final VisitorRequestService service;

    public VisitorRequestInternalController(VisitorRequestService service) {
        this.service = service;
    }

    @PostMapping("/{id}/verified")
    @Operation(summary = "auth-service confirms the email OTP and supplies the visitor's identity")
    public ApiResponse<VisitorRequestResponse> markVerified(
            @PathVariable @Positive Long id,
            @Valid @RequestBody VisitorEmailVerifiedDto dto) {

        return ApiResponse.ok("Email verified", service.markEmailVerified(id, dto));
    }

    /**
     * PROPOSAL. The same operation keyed by the visitor's email.
     *
     * The sibling above cannot be called by the only service that would ever
     * call it: auth-service has an email after an OTP, never a request id. This
     * is the shape that makes the integration expressible. See
     * VisitorRequestService.markEmailVerifiedByEmail for why identity still
     * comes from the body rather than the path.
     */
    @PostMapping("/by-email/{email}/verified")
    @Operation(summary = "auth-service confirms the email OTP, addressing the request by email")
    public ApiResponse<VisitorRequestResponse> markVerifiedByEmail(
            @PathVariable @NotBlank @Size(max = 180) String email,
            @Valid @RequestBody VisitorEmailVerifiedDto dto) {

        return ApiResponse.ok("Email verified", service.markEmailVerifiedByEmail(email, dto));
    }
}
