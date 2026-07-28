package com.perimity.qr.dto;

import com.perimity.qr.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST /api/internal/qr/decrypt - guard-service sends a scanned token.
 *
 * This is the only DTO in the service that carries attacker-controlled input:
 * whatever was physically in the QR code someone held up to a camera. Every
 * rule below exists to make a malformed or oversized token a cheap 400
 * instead of an AES operation on junk.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrDecryptRequest {

    /**
     * @Size runs before @Pattern in practice for oversized input, which is the
     * point: a regex over a multi-megabyte string is a denial-of-service, a
     * length check is not. The 512 ceiling matches ValidationPatterns.QR_TOKEN.
     */
    @NotBlank(message = "token is required")
    @Size(max = 512, message = "token is too long to be a Perimity token")
    @Pattern(regexp = ValidationPatterns.QR_TOKEN, message = ValidationPatterns.QR_TOKEN_MESSAGE)
    private String token;

    /**
     * Which gate the scan happened at. qr-service does not decide access, so
     * this is not used to accept or reject - it is carried through so the
     * decrypt attempt can be traced back to a physical gate.
     */
    @Positive(message = "gateId must be a positive id when present")
    private Long gateId;
}
