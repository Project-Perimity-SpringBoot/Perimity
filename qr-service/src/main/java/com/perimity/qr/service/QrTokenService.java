package com.perimity.qr.service;

import com.perimity.qr.dto.QrGenerateRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Produces and reads the AES-256 pass token. No database access lives here -
 * this class is pure crypto so it can be unit tested without Postgres.
 *
 * The token that travels in the QR code is:
 *
 *     BASE64URL( iv[12] || AES-256-GCM(payload) || tag[16] )
 *
 * GCM rather than CBC because GCM is authenticated: a token altered by even
 * one character fails to decrypt rather than decrypting into garbage that the
 * scan path then has to sanity check. That difference matters at a gate - a
 * forged token must be an exception, not a plausible-looking payload.
 *
 * The IV is random per token and prepended, never reused. Reusing a GCM IV
 * with the same key is the one mistake that breaks GCM outright, so it is
 * generated fresh on every call and never derived from the pass.
 *
 * What is stored in qr_records is only sha256(token). The token itself is
 * returned to the caller once, put into the PNG, and never persisted.
 */
@Service
public class QrTokenService {

    /** Version prefix. If the payload format ever changes, old tokens stay readable. */
    private static final String PAYLOAD_VERSION = "v1";
    private static final String FIELD_SEPARATOR = "|";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int AES_256_KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * The key arrives Base64-encoded from QR_AES_KEY. It is validated
     * here at startup rather than on first use: a wrong-length key should stop
     * the service from booting, not surface as a failed QR generation at 9am
     * on demo day.
     */
    public QrTokenService(@Value("${qr.token.secret}") String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException(
                    "qr.token.secret is not set. Add QR_AES_KEY to the repo-root .env "
                    + "and make sure it reaches the JVM environment - generate a value "
                    + "with: openssl rand -base64 32");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Secret.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("qr.token.secret is not valid Base64", ex);
        }

        if (keyBytes.length != AES_256_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "qr.token.secret must decode to exactly 32 bytes for AES-256, got "
                    + keyBytes.length + ". Generate one with: openssl rand -base64 32");
        }

        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Builds the token for one pass.
     *
     * Deliberately carries no personal data - no name, no email, no photo key.
     * A QR code is printed, photographed and shared; anything inside it should
     * be assumed public once decrypted. Identity is looked up from passId at
     * scan time via user-service, which is also what keeps the token short
     * enough to stay inside ValidationPatterns.QR_TOKEN's 512-character bound.
     */
    public String generateToken(QrGenerateRequest request) {
        String payload = PAYLOAD_VERSION
                + FIELD_SEPARATOR + request.getPassId()
                + FIELD_SEPARATOR + request.getCampusId()
                + FIELD_SEPARATOR + request.getValidFrom()
                + FIELD_SEPARATOR + (request.getValidTo() == null ? "" : request.getValidTo());

        return encrypt(payload);
    }

    /**
     * Reverses generateToken. Used by the Day 11 scan path.
     *
     * Any failure - wrong key, tampered bytes, truncated scan - surfaces as
     * IllegalArgumentException rather than a checked crypto exception, because
     * every one of them means the same thing to the caller: this is not a
     * token we issued.
     */
    public TokenPayload decryptToken(String token) {
        String payload = decrypt(token);
        String[] parts = payload.split("\\" + FIELD_SEPARATOR, -1);

        if (parts.length != 5 || !PAYLOAD_VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Unrecognised token payload format");
        }

        try {
            return new TokenPayload(
                    Long.parseLong(parts[1]),
                    Long.parseLong(parts[2]),
                    LocalDate.parse(parts[3]),
                    parts[4].isEmpty() ? null : LocalDate.parse(parts[4]));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Token payload could not be parsed", ex);
        }
    }

    /**
     * SHA-256 of the token, lower-case hex, 64 characters.
     *
     * This is the only form of the token that reaches the database. It must
     * match ValidationPatterns.SHA256_HEX or the entity's own @Pattern will
     * reject the row - that tripwire exists so a plain token can never be
     * written into token_hash by mistake.
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", ex);
        }
    }

    private String encrypt(String payload) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] cipherText = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            // withoutPadding: "=" is legal in the QR_TOKEN pattern but pointless
            // here, and a shorter token is a denser, easier-to-scan QR code.
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Token encryption failed", ex);
        }
    }

    private String decrypt(String token) {
        byte[] combined;
        try {
            combined = Base64.getUrlDecoder().decode(token);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Token is not valid URL-safe Base64", ex);
        }

        if (combined.length <= IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Token is too short to contain an IV");
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);

            byte[] cipherText = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            // Covers AEADBadTagException (tampered or forged) and everything else.
            // The message stays vague on purpose - a caller probing the gate
            // should not learn which part of their guess was wrong.
            throw new IllegalArgumentException("Token could not be decrypted", ex);
        }
    }

    /**
     * What a decrypted token contains. Not a wire DTO - it never leaves the
     * service layer, so it lives here rather than in the dto package.
     */
    public record TokenPayload(Long passId, Long campusId, LocalDate validFrom, LocalDate validTo) {

        /** Shape check only, mirroring QrRecord.isUsableOn. */
        public boolean isUsableOn(LocalDate day) {
            if (validFrom == null || day.isBefore(validFrom)) {
                return false;
            }
            return validTo == null || !day.isAfter(validTo);
        }
    }
}