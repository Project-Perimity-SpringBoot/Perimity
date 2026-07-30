package com.perimity.gatepass.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/gatepass/internal/visitor-requests/{id}/verified
 *
 * auth-service calls this once it has confirmed the visitor's email OTP, and
 * sends the identity it created or matched for that address.
 *
 * Two things happen at once here on purpose: the request becomes verified AND
 * it gains a holder. Splitting them would allow a verified request with no
 * identity, which is a request that can never become a pass.
 *
 * This is an INTERNAL endpoint. Day 7 puts it behind the shared internal API
 * key so a visitor cannot call it and verify their own request.
 */
@Schema(description = "auth-service confirms a visitor's email and supplies their identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitorEmailVerifiedDto {

    @NotNull(message = "The visitor's user id is required")
    @Positive(message = "User id must be a positive number")
    @Schema(description = "The VISITOR identity auth-service created or matched by email",
            example = "204")
    private Long visitorUserId;
}
