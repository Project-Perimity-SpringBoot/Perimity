package com.perimity.qr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perimity.qr.dto.DecryptFailureReason;
import com.perimity.qr.dto.QrDecryptRequest;
import com.perimity.qr.dto.QrDecryptResponse;
import com.perimity.qr.entity.QrRecord;
import com.perimity.qr.repository.QrRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The scan path, branch by branch.
 *
 * A REAL QrTokenService is used rather than a mock, deliberately. The point of
 * most of these tests is that AES-GCM behaves the way the design claims - that
 * a tampered token throws instead of decoding, that a token from another key is
 * refused. A mocked token service would prove none of that; it would only prove
 * the mock returned what the test told it to.
 *
 * Only the repository is mocked, because that is genuinely external.
 */
class QrDecryptServiceTest {

    /** A real 32-byte AES key. Test-only, never used anywhere else. */
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private QrTokenService tokenService;
    private QrRecordRepository repository;
    private QrDecryptService service;

    @BeforeEach
    void setUp() {
        tokenService = new QrTokenService(KEY);
        repository = Mockito.mock(QrRecordRepository.class);
        service = new QrDecryptService(tokenService, repository);
    }

    @Test
    @DisplayName("a live token for an active record is accepted")
    void acceptsLiveToken() {
        String token = issueToken(7L, 1L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(3));
        when(repository.findByTokenHash(tokenService.hashToken(token)))
                .thenReturn(Optional.of(record(7L, 1L, true,
                        LocalDate.now().minusDays(1), LocalDate.now().plusDays(3))));

        QrDecryptResponse result = service.decrypt(request(token));

        assertThat(result.isTokenValid()).isTrue();
        assertThat(result.getPassId()).isEqualTo(7L);
        assertThat(result.getCampusId()).isEqualTo(1L);
        assertThat(result.isWithinValidityWindow()).isTrue();
        assertThat(result.getReason()).isNull();
    }

    @Test
    @DisplayName("an expired token stays VALID - the date is guard-service's decision")
    void expiredTokenIsStillAValidToken() {
        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to = LocalDate.now().minusDays(20);

        String token = issueToken(7L, 1L, from, to);
        when(repository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(record(7L, 1L, true, from, to)));

        QrDecryptResponse result = service.decrypt(request(token));

        /*
         * The single most counter-intuitive assertion in this class, and the
         * one worth defending in a viva.
         *
         * tokenValid answers "did we issue this and is it still current". The
         * answer is yes. guard-service reads !tokenValid as INVALID_TOKEN, so
         * flipping this to false would show the guard "invalid pass" for
         * someone whose pass merely ran out - the wrong reason, and one the
         * visitor cannot act on.
         */
        assertThat(result.isTokenValid()).isTrue();
        assertThat(result.isWithinValidityWindow()).isFalse();
        assertThat(result.getReason()).isNull();
    }

    @Test
    @DisplayName("a token whose record was invalidated is refused as SUPERSEDED, with its passId")
    void refusesSupersededToken() {
        String token = issueToken(7L, 1L, LocalDate.now(), LocalDate.now().plusDays(1));
        QrRecord retired = record(7L, 1L, false, LocalDate.now(), LocalDate.now().plusDays(1));
        retired.setInvalidatedAt(LocalDateTime.now());
        retired.setInvalidatedReason("Re-issued");

        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(retired));

        QrDecryptResponse result = service.decrypt(request(token));

        assertThat(result.isTokenValid()).isFalse();
        assertThat(result.getReason()).isEqualTo(DecryptFailureReason.TOKEN_SUPERSEDED.name());
        // passId IS returned: the caller already proved it holds a genuine
        // token for that pass, so there is nothing left to withhold, and the
        // denial needs to name a pass to be actionable.
        assertThat(result.getPassId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("a tampered token is TOKEN_UNREADABLE and never reaches the database")
    void refusesTamperedToken() {
        String token = issueToken(7L, 1L, LocalDate.now(), LocalDate.now().plusDays(1));

        // Flip a bit in the decoded ciphertext, not in the Base64 string: an
        // unpadded final character can carry as few as two significant bits,
        // so changing it may decode to identical bytes.
        byte[] raw = java.util.Base64.getUrlDecoder().decode(token);
        raw[20] ^= 0x01;
        String tampered = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        QrDecryptResponse result = service.decrypt(request(tampered));

        assertThat(result.isTokenValid()).isFalse();
        assertThat(result.getReason()).isEqualTo(DecryptFailureReason.TOKEN_UNREADABLE.name());
        assertThat(result.getPassId()).isNull();
        // Cheap rejection: a forged scan must not cost a database round trip.
        verify(repository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("a token minted with a different key is refused")
    void refusesForeignKeyToken() {
        QrTokenService otherKey = new QrTokenService("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        String foreign = otherKey.generateToken(
                generateRequest(7L, 1L, LocalDate.now(), LocalDate.now().plusDays(1)));

        QrDecryptResponse result = service.decrypt(request(foreign));

        assertThat(result.isTokenValid()).isFalse();
        assertThat(result.getReason()).isEqualTo(DecryptFailureReason.TOKEN_UNREADABLE.name());
        verify(repository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("a token that decrypts but has no stored row is TOKEN_UNKNOWN")
    void refusesUnknownToken() {
        String token = issueToken(7L, 1L, LocalDate.now(), LocalDate.now().plusDays(1));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        QrDecryptResponse result = service.decrypt(request(token));

        assertThat(result.isTokenValid()).isFalse();
        assertThat(result.getReason()).isEqualTo(DecryptFailureReason.TOKEN_UNKNOWN.name());
        assertThat(result.getPassId()).isNull();
    }

    @Test
    @DisplayName("payload and stored row disagreeing on the pass is refused, not admitted")
    void refusesMismatchedRecord() {
        String token = issueToken(7L, 1L, LocalDate.now(), LocalDate.now().plusDays(1));
        // Row says a different pass. Impossible through any normal path - which
        // is why it must never quietly open a gate.
        when(repository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(record(99L, 1L, true,
                        LocalDate.now(), LocalDate.now().plusDays(1))));

        QrDecryptResponse result = service.decrypt(request(token));

        assertThat(result.isTokenValid()).isFalse();
        assertThat(result.getReason()).isEqualTo(DecryptFailureReason.TOKEN_MISMATCH.name());
        assertThat(result.getPassId()).isNull();
    }

    @Test
    @DisplayName("a not-yet-valid token is valid but outside its window")
    void futureTokenIsValidButOutsideWindow() {
        LocalDate from = LocalDate.now().plusDays(5);
        LocalDate to = LocalDate.now().plusDays(6);

        String token = issueToken(7L, 1L, from, to);
        when(repository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(record(7L, 1L, true, from, to)));

        QrDecryptResponse result = service.decrypt(request(token));

        assertThat(result.isTokenValid()).isTrue();
        assertThat(result.isWithinValidityWindow()).isFalse();
    }

    @Test
    @DisplayName("an open-ended DAILY token with no end date is inside its window")
    void openEndedTokenIsInsideWindow() {
        LocalDate from = LocalDate.now().minusDays(2);

        String token = issueToken(7L, 1L, from, null);
        when(repository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(record(7L, 1L, true, from, null)));

        QrDecryptResponse result = service.decrypt(request(token));

        assertThat(result.isTokenValid()).isTrue();
        assertThat(result.isWithinValidityWindow()).isTrue();
    }

    // ---------- fixtures ----------

    private String issueToken(Long passId, Long campusId, LocalDate from, LocalDate to) {
        return tokenService.generateToken(generateRequest(passId, campusId, from, to));
    }

    private com.perimity.qr.dto.QrGenerateRequest generateRequest(
            Long passId, Long campusId, LocalDate from, LocalDate to) {
        return com.perimity.qr.dto.QrGenerateRequest.builder()
                .passId(passId)
                .campusId(campusId)
                .validFrom(from)
                .validTo(to)
                .build();
    }

    private QrDecryptRequest request(String token) {
        return QrDecryptRequest.builder().token(token).gateId(3L).build();
    }

    private QrRecord record(Long passId, Long campusId, boolean active,
                            LocalDate from, LocalDate to) {
        return QrRecord.builder()
                .id(1L)
                .passId(passId)
                .campusId(campusId)
                .tokenHash("a".repeat(64))
                .validFrom(from)
                .validTo(to)
                .active(active)
                .build();
    }
}
