package com.perimity.gatepass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perimity.gatepass.dto.request.GatePassStatusUpdateDto;
import com.perimity.gatepass.dto.request.HolderPauseDto;
import com.perimity.gatepass.dto.request.PassActivationDto;
import com.perimity.gatepass.entity.GatePass;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.entity.enums.PassType;
import com.perimity.gatepass.messaging.QrJobPublisher;
import com.perimity.gatepass.repository.EventRepository;
import com.perimity.gatepass.repository.GatePassRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The pass lifecycle state machine.
 *
 * No Spring context, no database - these are business rules and they should
 * run in milliseconds. The state machine is the single most important piece of
 * logic in this service: it is what stands between a revoked pass and an open
 * gate.
 *
 * The legal graph, from PassStatus:
 *     PENDING -> ACTIVE, REVOKED
 *     ACTIVE  -> PAUSED, EXPIRED, REVOKED
 *     PAUSED  -> ACTIVE, REVOKED
 *     EXPIRED, REVOKED -> nothing. Both terminal.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GatePassService - the pass lifecycle")
class GatePassServiceTest {

    private static final Long CAMPUS = 1L;

    @Mock private GatePassRepository passRepository;
    @Mock private EventRepository eventRepository;
    @Mock private QrJobPublisher qrJobPublisher;

    @InjectMocks private GatePassService service;

    @Nested
    @DisplayName("status transitions")
    class Transitions {

        @Test
        @DisplayName("ACTIVE -> PAUSED records the reason")
        void activeToPaused() {
            GatePass pass = pass(PassStatus.ACTIVE);
            when(passRepository.findByIdAndCampusId(1L, CAMPUS)).thenReturn(Optional.of(pass));
            when(passRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.changeStatus(CAMPUS, 1L, GatePassStatusUpdateDto.builder()
                    .targetStatus(PassStatus.PAUSED)
                    .reason("Government id under re-verification")
                    .changedBy(9L)
                    .build());

            assertThat(pass.getStatus()).isEqualTo(PassStatus.PAUSED);
            assertThat(pass.getPausedReason()).isEqualTo("Government id under re-verification");
        }

        @Test
        @DisplayName("PAUSED -> ACTIVE CLEARS the paused reason")
        void resumeClearsReason() {
            // If this is not cleared, the wallet screen shows a live pass that
            // still displays why it was once held.
            GatePass pass = pass(PassStatus.PAUSED);
            pass.setPausedReason("Was under review");
            when(passRepository.findByIdAndCampusId(1L, CAMPUS)).thenReturn(Optional.of(pass));
            when(passRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.changeStatus(CAMPUS, 1L, GatePassStatusUpdateDto.builder()
                    .targetStatus(PassStatus.ACTIVE).changedBy(9L).build());

            assertThat(pass.getStatus()).isEqualTo(PassStatus.ACTIVE);
            assertThat(pass.getPausedReason()).isNull();
        }

        @Test
        @DisplayName("REVOKED is terminal - it cannot be brought back to ACTIVE")
        void revokedIsTerminal() {
            GatePass pass = pass(PassStatus.REVOKED);
            when(passRepository.findByIdAndCampusId(1L, CAMPUS)).thenReturn(Optional.of(pass));

            assertThatThrownBy(() -> service.changeStatus(CAMPUS, 1L,
                    GatePassStatusUpdateDto.builder()
                            .targetStatus(PassStatus.ACTIVE).changedBy(9L).build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot become active");

            verify(passRepository, never()).save(any());
        }

        @Test
        @DisplayName("EXPIRED is terminal too")
        void expiredIsTerminal() {
            GatePass pass = pass(PassStatus.EXPIRED);
            when(passRepository.findByIdAndCampusId(1L, CAMPUS)).thenReturn(Optional.of(pass));

            assertThatThrownBy(() -> service.changeStatus(CAMPUS, 1L,
                    GatePassStatusUpdateDto.builder()
                            .targetStatus(PassStatus.ACTIVE).changedBy(9L).build()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("revoking writes reason, actor and timestamp together")
        void revokeWritesAudit() {
            GatePass pass = pass(PassStatus.ACTIVE);
            when(passRepository.findByIdAndCampusId(1L, CAMPUS)).thenReturn(Optional.of(pass));
            when(passRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.changeStatus(CAMPUS, 1L, GatePassStatusUpdateDto.builder()
                    .targetStatus(PassStatus.REVOKED)
                    .reason("Lost badge reported")
                    .changedBy(77L)
                    .build());

            assertThat(pass.getStatus()).isEqualTo(PassStatus.REVOKED);
            assertThat(pass.getRevokedReason()).isEqualTo("Lost badge reported");
            assertThat(pass.getRevokedBy()).isEqualTo(77L);
            assertThat(pass.getRevokedAt()).isNotNull();
        }

        @Test
        @DisplayName("a no-op transition is rejected rather than silently accepted")
        void sameStatusRejected() {
            GatePass pass = pass(PassStatus.ACTIVE);
            when(passRepository.findByIdAndCampusId(1L, CAMPUS)).thenReturn(Optional.of(pass));

            assertThatThrownBy(() -> service.changeStatus(CAMPUS, 1L,
                    GatePassStatusUpdateDto.builder()
                            .targetStatus(PassStatus.ACTIVE).changedBy(9L).build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already active");
        }
    }

    @Nested
    @DisplayName("activation from qr-service")
    class Activation {

        @Test
        @DisplayName("PENDING -> ACTIVE stores both object keys")
        void activateStoresKeys() {
            GatePass pass = pass(PassStatus.PENDING);
            when(passRepository.findById(1L)).thenReturn(Optional.of(pass));
            when(passRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.activate(1L, PassActivationDto.builder()
                    .qrKey("north/passes/pass-1-qr.png")
                    .pdfKey("north/passes/pass-1.pdf")
                    .build());

            assertThat(pass.getStatus()).isEqualTo(PassStatus.ACTIVE);
            assertThat(pass.getQrKey()).isEqualTo("north/passes/pass-1-qr.png");
            assertThat(pass.getPdfKey()).isEqualTo("north/passes/pass-1.pdf");
        }

        @Test
        @DisplayName("IDEMPOTENT - a redelivered RabbitMQ result is not an error")
        void activateTwiceIsFine() {
            // RabbitMQ guarantees at-least-once delivery. The same result WILL
            // sometimes arrive twice; throwing would turn normal broker
            // behaviour into a logged error and a dead-lettered message.
            GatePass pass = pass(PassStatus.ACTIVE);
            when(passRepository.findById(1L)).thenReturn(Optional.of(pass));

            service.activate(1L, PassActivationDto.builder()
                    .qrKey("k.png").pdfKey("k.pdf").build());

            verify(passRepository, never()).save(any());
        }

        @Test
        @DisplayName("a pass revoked mid-generation refuses to activate")
        void revokedDuringGeneration() {
            // The window is real: revoke happens while qr-service is still
            // rendering. Without this the pass would go green afterwards.
            GatePass pass = pass(PassStatus.REVOKED);
            when(passRepository.findById(1L)).thenReturn(Optional.of(pass));

            assertThatThrownBy(() -> service.activate(1L, PassActivationDto.builder()
                    .qrKey("k.png").pdfKey("k.pdf").build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("revoked");
        }
    }

    @Nested
    @DisplayName("bulk operations")
    class Bulk {

        @Test
        @DisplayName("pauseAllForHolder touches only ACTIVE passes")
        void pauseOnlyActive() {
            GatePass a = pass(PassStatus.ACTIVE);
            GatePass b = pass(PassStatus.ACTIVE);
            when(passRepository.findByHolderUserIdAndStatusOrderByCreatedAtDesc(5L, PassStatus.ACTIVE))
                    .thenReturn(List.of(a, b));

            service.pauseAllForHolder(5L, HolderPauseDto.builder()
                    .reason("Sensitive profile field changed").changedBy(3L).build());

            assertThat(a.getStatus()).isEqualTo(PassStatus.PAUSED);
            assertThat(b.getStatus()).isEqualTo(PassStatus.PAUSED);
            verify(passRepository).saveAll(List.of(a, b));
        }

        @Test
        @DisplayName("revokeAllForEvent revokes PENDING as well as ACTIVE and PAUSED")
        void cancelRevokesPendingToo() {
            // PENDING is the one people forget. Leave it and qr-service
            // finishes generating, calls activate, and the pass goes GREEN
            // minutes after the event was called off.
            GatePass active = pass(PassStatus.ACTIVE);
            GatePass pending = pass(PassStatus.PENDING);
            GatePass paused = pass(PassStatus.PAUSED);
            GatePass alreadyDead = pass(PassStatus.EXPIRED);

            when(passRepository.findByEventId(7L))
                    .thenReturn(List.of(active, pending, paused, alreadyDead));

            int revoked = service.revokeAllForEvent(7L, "Event cancelled: AI Summit", 4L);

            assertThat(revoked).isEqualTo(3);
            assertThat(active.getStatus()).isEqualTo(PassStatus.REVOKED);
            assertThat(pending.getStatus()).isEqualTo(PassStatus.REVOKED);
            assertThat(paused.getStatus()).isEqualTo(PassStatus.REVOKED);
            // Terminal already - untouched.
            assertThat(alreadyDead.getStatus()).isEqualTo(PassStatus.EXPIRED);
        }
    }

    @Nested
    @DisplayName("the expiry sweep")
    class Sweep {

        @Test
        @DisplayName("expireOne does nothing to a pass that is no longer ACTIVE")
        void sweepSkipsNonActive() {
            // Between the sweep listing ids and processing them, a pass may
            // have been revoked. Expiring it would overwrite the revocation.
            GatePass revoked = pass(PassStatus.REVOKED);
            when(passRepository.findById(1L)).thenReturn(Optional.of(revoked));

            service.expireOne(1L);

            assertThat(revoked.getStatus()).isEqualTo(PassStatus.REVOKED);
            verify(passRepository, never()).save(any());
        }

        @Test
        @DisplayName("expireOne tolerates a pass that has vanished")
        void sweepToleratesMissingRow() {
            when(passRepository.findById(404L)).thenReturn(Optional.empty());

            service.expireOne(404L);

            verify(passRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("re-queueing a stuck pass")
    class Republish {

        @Test
        @DisplayName("only a PENDING pass can be re-queued")
        void republishRejectsActive() {
            GatePass active = pass(PassStatus.ACTIVE);
            when(passRepository.findByIdAndCampusId(1L, CAMPUS)).thenReturn(Optional.of(active));

            assertThatThrownBy(() -> service.republishGenerationJob(CAMPUS, 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("pending");

            verify(qrJobPublisher, never()).publishAfterCommit(any());
        }

        @Test
        @DisplayName("a PENDING pass is republished")
        void republishesPending() {
            GatePass pending = pass(PassStatus.PENDING);
            when(passRepository.findByIdAndCampusId(1L, CAMPUS)).thenReturn(Optional.of(pending));

            service.republishGenerationJob(CAMPUS, 1L);

            verify(qrJobPublisher).publishAfterCommit(pending);
        }
    }

    // ------------------------------------------------------------- helper

    private GatePass pass(PassStatus status) {
        return GatePass.builder()
                .id(1L)
                .holderUserId(5L)
                .holderName("Asha Menon")
                .campusId(CAMPUS)
                .passType(PassType.DAILY)
                .validFrom(LocalDate.now())
                .status(status)
                .build();
    }
}
