package com.perimity.guard.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.perimity.guard.document.enums.DenialReason;
import com.perimity.guard.document.enums.PassType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The translation layer between gatepass-service's vocabulary and this one.
 *
 * ==========================================================================
 * WHY THIS IS TESTED SEPARATELY FROM THE HTTP CALL
 * ==========================================================================
 * denialFor() is the line of code that decides whether a person walks through a
 * gate. It happens to live in a class that also does HTTP, but the decision
 * itself needs no network, no Spring and no mock server - so it is tested
 * directly, and the test runs in microseconds and never flakes.
 *
 * The two-hop HTTP flow around it is worth an integration test, but against the
 * REAL qr-service and gatepass-service now that both endpoints exist. Mocking
 * their responses would test my guess at their JSON rather than their JSON.
 *
 * Constructing with nulls is deliberate: these two methods touch no field, and
 * proving that is part of the point. If someone later reaches for `qr`,
 * `gatepass` or either circuit breaker inside them, this test fails immediately
 * with an NPE and the decision logic has quietly grown a network dependency.
 *
 * The two breaker arguments are null for the same reason as the two clients.
 * Adding them widened the constructor but not the guarantee - a breaker is part
 * of how a hop is called, and denialFor() still calls nothing.
 */
class HttpPassVerificationClientMappingTest {

    private final HttpPassVerificationClient client =
            new HttpPassVerificationClient(null, null, null, null);

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PENDING, PASS_PENDING",
            "PAUSED,  PASS_PAUSED",
            "EXPIRED, PASS_EXPIRED",
            "REVOKED, PASS_REVOKED"
    })
    @DisplayName("every non-active pass status maps to its own reason, never a generic one")
    void statusMapsToItsOwnReason(String status, DenialReason expected) {
        assertThat(client.denialFor(status)).isEqualTo(expected);
    }

    @Test
    @DisplayName("ACTIVE raises no objection - but that is not the same as saying yes")
    void activeRaisesNoObjection() {
        // null means "the lifecycle has nothing against this pass". ScanService
        // still checks campus and date range afterwards, so a null here is one
        // hurdle cleared, not the verdict.
        assertThat(client.denialFor("ACTIVE")).isNull();
    }

    @Test
    @DisplayName("an unknown status refuses entry rather than throwing")
    void unknownStatusRefuses() {
        // The day Tushar adds a status this service has not heard of, the gate
        // must keep working. Refusing one pass is recoverable; a 500 on every
        // scan of that status is a queue at the gate and nobody knowing why.
        assertThat(client.denialFor("SUSPENDED_PENDING_REVIEW"))
                .isEqualTo(DenialReason.INVALID_TOKEN);
    }

    @Test
    @DisplayName("a null status refuses too - absence is not permission")
    void nullStatusRefuses() {
        assertThat(client.denialFor(null)).isEqualTo(DenialReason.INVALID_TOKEN);
    }

    @Test
    @DisplayName("pass types map, and anything unrecognised falls back to DAILY")
    void passTypeMapping() {
        assertThat(client.passTypeFor("EVENT")).isEqualTo(PassType.EVENT);
        assertThat(client.passTypeFor("DAILY")).isEqualTo(PassType.DAILY);

        // DAILY is the safe fallback because it is the type that triggers the
        // Behavior 2 lookup. Guessing EVENT would credit an entry to an event the
        // person may have nothing to do with, quietly corrupting attendance;
        // guessing DAILY at worst asks gatepass-service a question it answers no to.
        assertThat(client.passTypeFor("SEASONAL")).isEqualTo(PassType.DAILY);
        assertThat(client.passTypeFor(null)).isEqualTo(PassType.DAILY);
    }
}
