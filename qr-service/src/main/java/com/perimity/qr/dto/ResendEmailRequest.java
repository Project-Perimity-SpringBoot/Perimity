package com.perimity.qr.dto;

import com.perimity.qr.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/qr/internal/{passId}/resend-email.
 *
 * The address is required from the caller rather than looked up, because
 * qr-service does not store holder emails and should not start doing so to
 * make an error path convenient. See PassEmailRetryService for the full
 * reasoning - it is a data-minimisation decision, not an omission.
 *
 * subject and body are optional. Omitted, PassEmailService falls back to
 * neutral wording. Supplied, they should be the same strings gatepass-service
 * composed, so the resent email reads identically to the one that failed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResendEmailRequest {

    @NotBlank(message = "A recipient email address is required")
    @Size(max = 254, message = "That address is too long to be a real email address")
    @Pattern(regexp = ValidationPatterns.EMAIL, message = ValidationPatterns.EMAIL_MESSAGE)
    private String email;

    /** @Size matches nothing in the database - it bounds what a mail server will accept. */
    @Size(max = 200, message = "Subject may be at most 200 characters")
    private String subject;

    @Size(max = 5000, message = "Body may be at most 5000 characters")
    private String body;
}
