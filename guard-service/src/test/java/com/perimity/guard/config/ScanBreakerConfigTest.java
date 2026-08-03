package com.perimity.guard.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * The breaker's job is to distinguish "the peer is unwell" from "somebody
 * presented a bad QR". Those look identical from inside a try/catch and are
 * opposite situations, so the distinction is worth a test rather than a comment.
 *
 * No Spring context. The config object is constructed directly and its @Value
 * fields set, because what is being tested is the CircuitBreakerConfig it
 * builds, not whether Spring can inject an int.
 */
class ScanBreakerConfigTest {

    /** Small window and low minimum, so a test can reach a decision quickly. */
    private CircuitBreaker breaker() {
        ScanBreakerConfig config = new ScanBreakerConfig();
        ReflectionTestUtils.setField(config, "windowSize", 10);
        ReflectionTestUtils.setField(config, "minimumCalls", 5);
        ReflectionTestUtils.setField(config, "failureRatePercent", 50f);
        ReflectionTestUtils.setField(config, "openSeconds", 10L);
        return config.qrDecryptBreaker();
    }

    @Test
    @DisplayName("a run of rejected tokens never opens the breaker - 4xx is normal traffic at a gate")
    void clientErrorsNeverOpenTheBreaker() {
        CircuitBreaker breaker = breaker();

        // Fifty consecutive refusals. This is a plausible morning: a stale
        // screenshot, an expired pass, somebody scanning the wrong QR.
        for (int i = 0; i < 50; i++) {
            assertThatThrownBy(() -> breaker.executeSupplier(() -> {
                throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
            })).isInstanceOf(HttpClientErrorException.class);
        }

        assertThat(breaker.getState())
                .as("if this is OPEN, anyone holding a revoked pass can close the gate "
                        + "by scanning it repeatedly")
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    @DisplayName("a peer that is genuinely down does open it")
    void transportFailuresOpenTheBreaker() {
        CircuitBreaker breaker = breaker();

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> breaker.executeSupplier(() -> {
                throw new ResourceAccessException("Connection refused");
            })).isInstanceOf(RuntimeException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("a peer returning 500 also opens it - that is the peer being unwell, not the token")
    void serverErrorsOpenTheBreaker() {
        CircuitBreaker breaker = breaker();

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> breaker.executeSupplier(() -> {
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
            })).isInstanceOf(RuntimeException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("once open, the peer is not called at all - this is the entire point")
    void openBreakerDoesNotTouchTheNetwork() {
        CircuitBreaker breaker = breaker();
        AtomicInteger attempts = new AtomicInteger();

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> breaker.executeSupplier(() -> {
                attempts.incrementAndGet();
                throw new ResourceAccessException("Connection refused");
            })).isInstanceOf(RuntimeException.class);
        }
        int attemptsBeforeOpen = attempts.get();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Twenty more scans while it is open. Without the breaker each would
        // wait out the 400ms connect timeout; with it, none reaches the supplier.
        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> breaker.executeSupplier(() -> {
                attempts.incrementAndGet();
                throw new ResourceAccessException("Connection refused");
            })).isInstanceOf(CallNotPermittedException.class);
        }

        assertThat(attempts.get())
                .as("an open breaker must suppress the call, not merely re-label its failure")
                .isEqualTo(attemptsBeforeOpen);
    }

    @Test
    @DisplayName("the two hops get independent breakers")
    void hopsDoNotShareABreaker() {
        ScanBreakerConfig config = new ScanBreakerConfig();
        ReflectionTestUtils.setField(config, "windowSize", 10);
        ReflectionTestUtils.setField(config, "minimumCalls", 5);
        ReflectionTestUtils.setField(config, "failureRatePercent", 50f);
        ReflectionTestUtils.setField(config, "openSeconds", 10L);

        CircuitBreaker qr = config.qrDecryptBreaker();
        CircuitBreaker gatepass = config.gatepassPassBreaker();

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> qr.executeSupplier(() -> {
                throw new ResourceAccessException("Connection refused");
            })).isInstanceOf(RuntimeException.class);
        }

        assertThat(qr.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(gatepass.getState())
                .as("a sick qr-service must not suppress calls to a healthy gatepass-service")
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
