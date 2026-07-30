package com.perimity.auth.controller;

import com.perimity.auth.dto.ApiResponse;
import com.perimity.auth.dto.request.InternalIdentityBatchDto;
import com.perimity.auth.dto.request.InternalIdentityCreateDto;
import com.perimity.auth.dto.response.IdentityBatchResponseDto;
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
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * Day 8 gave the single-row lookup and resolve. Day 10 adds the batch, which is
 * what the bulk engine actually calls: one round trip for a 600-row sheet
 * instead of 600, and no exception when one row is refused.
 */
@RestController
@RequestMapping("/api/internal/auth/users")
@Validated
@Tag(name = "Internal - Accounts", description = "Service-to-service identity lookup")
public class InternalUserController {

    private final UserAccountService service;
    private final int maxRows;

    public InternalUserController(UserAccountService service,
                                  @Value("${perimity.bulk.max-rows}") int maxRows) {
        this.service = service;
        this.maxRows = maxRows;
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

    /**
     * Day 10. Added because gatepass-service's InternalServiceClient asks for a
     * userId and wants an email back, which is the opposite direction from
     * /by-email above. Without this it has no working way to enrich a QR job
     * with the holder's address.
     */
    @GetMapping("/{userId}/email")
    @Operation(summary = "The email for a known user id. The reverse of /by-email, "
            + "for callers holding an id rather than an address.")
    public ApiResponse<UserResponse> emailOf(@PathVariable @Positive Long userId) {
        return ApiResponse.ok(service.getOne(userId));
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

    /**
     * Day 10, the slow phase - called once after the uploader clicks Confirm.
     *
     * Always 200, never 201, even when it created 478 identities. A batch has no
     * single created resource to point a Location header at, and answering 201
     * for a batch that also refused 15 rows and skipped 5 duplicates would
     * describe it wrongly. The per-row outcomes are the answer; the status only
     * says the batch ran.
     */
    @PostMapping("/batch")
    @Operation(summary = "Resolve or create many identities in one call. Returns 200 with "
            + "per-row outcomes - one refused row never fails the batch.")
    public ApiResponse<IdentityBatchResponseDto> resolveOrCreateBatch(
            @Valid @RequestBody InternalIdentityBatchDto dto) {

        if (dto.getRows().size() > maxRows) {
            throw new IllegalArgumentException(
                    "This request has " + dto.getRows().size()
                            + " rows. The maximum is " + maxRows + ".");
        }
        return ApiResponse.ok("Batch resolved", service.resolveOrCreateBatch(dto));
    }
}
