package com.perimity.auth.dto.response;

import com.perimity.auth.entity.enums.OtpPurpose;
import java.time.LocalDateTime;

/**
 * The answer to "send me a code".
 *
 * It does NOT contain the code. Not in any field, not for debugging, not behind
 * a dev flag. A code returned in the HTTP response has travelled to whoever is
 * watching the network tab, which defeats the entire purpose of emailing it.
 *
 * The email comes back masked so the UI can show "we sent a code to a***a@e***.com"
 * without the response itself confirming a full address to an attacker who
 * guessed it.
 */
public record OtpChallengeResponse(
        String maskedEmail,
        OtpPurpose purpose,
        LocalDateTime expiresAt,
        int attemptsAllowed
) {

    public static OtpChallengeResponse of(String email, OtpPurpose purpose,
                                          LocalDateTime expiresAt, int attemptsAllowed) {
        return new OtpChallengeResponse(mask(email), purpose, expiresAt, attemptsAllowed);
    }

    /** anita.deshmukh@example.com becomes a************h@e*********m */
    private static String mask(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at < 1 || at == email.length() - 1) {
            return "***";
        }
        return maskPart(email.substring(0, at)) + "@" + maskPart(email.substring(at + 1));
    }

    private static String maskPart(String part) {
        if (part.length() <= 2) {
            return "*".repeat(part.length());
        }
        return part.charAt(0) + "*".repeat(part.length() - 2) + part.charAt(part.length() - 1);
    }
}
