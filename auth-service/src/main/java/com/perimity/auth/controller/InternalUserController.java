package com.perimity.auth.controller;

import com.perimity.auth.dto.ApiResponse;
import com.perimity.auth.dto.request.InternalIdentityCreateDto;
import com.perimity.auth.dto.response.UserResponse;
import com.perimity.auth.exception.ResourceNotFoundException;
import com.perimity.auth.service.UserAccountService;
import com.perimity.auth.service.UserAccountService.IdentityResolution;
import com.perimity.auth.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only. Every path here is permitAll in SecurityConfig and
 * is actually guarded by InternalApiKeyFilter checking X-Internal-Api-Key -
 * never by a JWT, because the caller is a service, not a person with a token.
 *
 * Day 8. This is "the internal account lookup the other services need" -
 * gatepass-service's bulk engine resolving a spreadsheet row by email
 * (Event_Bulk_Design.md, "The Mixed-Attendee Problem"): existing identity is
 * reused, a brand-new email gets a lightweight VISITOR identity.
 */
@RestController
@RequestMapping("/api/internal/auth/users")
@Validated
@Tag(name = "Internal - Accounts", description = "Service-to-service identity lookup")
public class InternalUserController {

    private final UserAccountService service;

    public InternalUserController(UserAccountService service) {
        this.service = service;
    }

    @GetMapping("/by-email")
    @Operation(summary = "Does an identity exist for this email? 404 if not - the caller "
            + "decides whether that means 'create one'.")
    public ApiResponse<UserResponse> byEmail(
            @RequestParam @NotBlank @Pattern(regexp = ValidationPatterns.EMAIL,
                    message = ValidationPatterns.EMAIL_MESSAGE) String email) {

        return ApiResponse.ok(service.findByEmailForInternal(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account exists for that email.")));
    }

    @PostMapping
    @Operation(summary = "Resolve or create a lightweight VISITOR identity by email. "
            + "Idempotent - safe to call twice for the same row on a retry.")
    public ResponseEntity<ApiResponse<UserResponse>> resolveOrCreate(
            @Valid @RequestBody InternalIdentityCreateDto dto) {

        IdentityResolution result = service.resolveOrCreateInternalIdentity(dto);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = result.created() ? "Identity created" : "Existing identity reused";

        return ResponseEntity.status(status).body(ApiResponse.ok(message, result.user()));
    }
}
