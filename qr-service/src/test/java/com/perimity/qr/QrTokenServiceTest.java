package com.perimity.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.perimity.qr.dto.QrGenerateRequest;
import com.perimity.qr.service.QrTokenService;
import com.perimity.qr.validation.ValidationPatterns;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Day 5 evidence for the token layer.
 *
 * Plain JUnit, no @SpringBootTest: QrTokenService takes its key through the
 * constructor precisely so it can be tested without Postgres, RabbitMQ or an
 * application context. These run in milliseconds and will still pass on a
 * machine with Docker switched off.
 */
class QrTokenServiceTest {

    /** A throwaway 32-byte key. Never the real one - that lives only in .env. */
    private static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private QrTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new QrTokenService(TEST_KEY);
    }

    private QrGenerateRequest request(LocalDate from, LocalDate to) {
        return QrGenerateRequest.builder()
                .passId(42L)
                .campusId(7L)
                .validFrom(from)
                .validTo(to)
                .build();
    }

    @Test
    @DisplayName("a token decrypts back to exactly what went in")
    void roundTrip() {
        LocalDate from = LocalDate.of(2026, 7, 29);
        LocalDate to = LocalDate.of(2026, 8, 28);

        String token = tokenService.generateToken(request(from, to));
        QrTokenService.TokenPayload payload = tokenService.decryptToken(token);

        assertThat(payload.passId()).isEqualTo(42L);
        assertThat(payload.campusId()).isEqualTo(7L);
        assertThat(payload.validFrom()).isEqualTo(from);
        assertThat(payload.validTo()).isEqualTo(to);
    }

    @Test
    @DisplayName("a standing DAILY pass round-trips with a null end date")
    void roundTripWithNullValidTo() {
        String token = tokenService.generateToken(request(LocalDate.of(2026, 7, 29), null));

        QrTokenService.TokenPayload payload = tokenService.decryptToken(token);

        assertThat(payload.validTo()).isNull();
        assertThat(payload.passId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("the token satisfies the QR_TOKEN pattern guard/scan will check it against")
    void tokenMatchesTheAgreedPattern() {
        String token = tokenService.generateToken(request(LocalDate.now(), LocalDate.now().plusDays(30)));

        // If this fails, guard-service's @Pattern rejects every real token
        // before it ever reaches decrypt - a total outage at the gate.
        assertThat(token).matches(ValidationPatterns.QR_TOKEN);
    }

    @Test
    @DisplayName("the same pass generates a different token every time")
    void tokensAreNeverRepeated() {
        QrGenerateRequest identical = request(LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 28));

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            seen.add(tokenService.generateToken(identical));
        }

        // 50 identical inputs, 50 distinct outputs - proves the IV is random
        // per call. A repeat here would mean a reused GCM IV, which breaks the
        // cipher outright, and would also collide on the unique token_hash.
        assertThat(seen).hasSize(50);
    }

    @Test
    @DisplayName("the hash is a 64-character digest, not the token")
    void hashIsNotTheToken() {
        String token = tokenService.generateToken(request(LocalDate.now(), null));

        String hash = tokenService.hashToken(token);

        assertThat(hash).hasSize(64).matches(ValidationPatterns.SHA256_HEX);
        assertThat(hash).isNotEqualTo(token);
        assertThat(hash).doesNotContain(token);
    }

    @Test
    @DisplayName("hashing is deterministic, so a scan can look the token up")
    void hashingIsStable() {
        String token = tokenService.generateToken(request(LocalDate.now(), null));

        assertThat(tokenService.hashToken(token)).isEqualTo(tokenService.hashToken(token));
    }

    @Test
    @DisplayName("a tampered token is rejected, not silently decoded")
    void tamperedTokenIsRejected() {
        String token = tokenService.generateToken(request(LocalDate.now(), null));

        // Flip one character in the ciphertext body.
        char[] chars = token.toCharArray();
        chars[chars.length - 1] = chars[chars.length - 1] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        // This is the GCM authentication tag doing its job. With CBC this
        // would decrypt into garbage that the scan path would then have to
        // recognise as nonsense on its own.
        assertThatThrownBy(() -> tokenService.decryptToken(tampered))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a token from a different key is rejected")
    void wrongKeyIsRejected() {
        String token = tokenService.generateToken(request(LocalDate.now(), null));
        QrTokenService other = new QrTokenService("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");

        assertThatThrownBy(() -> other.decryptToken(token))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("junk input is rejected without an unchecked crypto exception escaping")
    void junkIsRejected() {
        assertThatThrownBy(() -> tokenService.decryptToken("not-a-real-token"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> tokenService.decryptToken(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the placeholder that was in .env is rejected, not silently accepted")
    void placeholderKeyFailsFast() {
        // "perimity_local_aes_key_32_chars!" - 32 CHARACTERS, which is what
        // made it look right. It is not a 32-BYTE key, and it is not even
        // valid Base64: "!" is outside the alphabet. macOS `base64 -d` is
        // lenient and reports 21 bytes; Java's decoder is strict and throws.
        // So this takes the Base64 branch, not the length branch.
        assertThatThrownBy(() -> new QrTokenService("perimity_local_aes_key_32_chars!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("valid Base64 of the wrong length is rejected on length")
    void shortKeyFailsFast() {
        // 16 bytes - valid Base64, valid AES-128 key, wrong for AES-256.
        assertThatThrownBy(() -> new QrTokenService("AAAAAAAAAAAAAAAAAAAAAA=="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("a missing key stops the service starting")
    void missingKeyFailsFast() {
        assertThatThrownBy(() -> new QrTokenService(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set");

        assertThatThrownBy(() -> new QrTokenService(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set");
    }
}
