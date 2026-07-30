package com.perimity.qr.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.perimity.qr.dto.QrGenerateRequest;
import com.perimity.qr.dto.QrRecordResponse;
import com.perimity.qr.entity.GenerationJob;
import com.perimity.qr.entity.enums.JobStatus;
import com.perimity.qr.messaging.contract.QrGenerationJob;
import com.perimity.qr.service.GenerationJobService;
import com.perimity.qr.service.QrRecordService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * Covers the branches where being wrong is silent - which is all of them that
 * matter here, because a mistake in this class does not throw, it just leaves a
 * pass stuck at PENDING or quietly breaks one that was working.
 *
 * A real Hibernate Validator is used rather than a mock: the point of the
 * invalid-payload tests is that QrGenerateRequest's own constraints actually run
 * against a queue message, and a mocked validator would only prove the mock was
 * called.
 */
class QrGenerationListenerTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private GenerationJobService jobService;
    private QrRecordService qrRecordService;
    private QrResultPublisher resultPublisher;
    private QrGenerationListener listener;

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        jobService = Mockito.mock(GenerationJobService.class);
        qrRecordService = Mockito.mock(QrRecordService.class);
        resultPublisher = Mockito.mock(QrResultPublisher.class);
        listener = new QrGenerationListener(validator, jobService, qrRecordService, resultPublisher);
    }

    @Test
    @DisplayName("a valid job generates, commits DONE, then publishes success")
    void happyPath() {
        QrGenerationJob message = job("uuid-1", 100L);
        GenerationJob row = row(10L, 0);

        when(jobService.claim(message)).thenReturn(row);
        when(qrRecordService.generate(any())).thenReturn(generated());

        listener.onGenerationJob(message);

        verify(jobService).markDone(10L);
        verify(resultPublisher).publishSuccess("uuid-1", 7L, 100L,
                "1/qr/7/3.png", "1/pdf/7/3.pdf", 1);
        verify(jobService, never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("a redelivery of a DONE job republishes its result instead of regenerating")
    void redeliveryRepublishesRatherThanRegenerating() {
        QrGenerationJob message = job("uuid-1", 100L);

        when(jobService.claim(message)).thenReturn(null);
        when(qrRecordService.findActiveByPassId(7L)).thenReturn(Optional.of(record()));

        listener.onGenerationJob(message);

        // The point: the holder's already-emailed token keeps working...
        verify(qrRecordService, never()).generate(any());
        // ...but gatepass is still told, in case the first result was lost.
        verify(resultPublisher).publishSuccess("uuid-1", 7L, 100L,
                "1/qr/7/3.png", "1/pdf/7/3.pdf", 1);
    }

    @Test
    @DisplayName("a DONE job whose QR was since revoked republishes a failure, not a success")
    void redeliveryAfterRevokeReportsFailure() {
        QrGenerationJob message = job("uuid-1", 100L);

        when(jobService.claim(message)).thenReturn(null);
        when(qrRecordService.findActiveByPassId(7L)).thenReturn(Optional.empty());

        listener.onGenerationJob(message);

        verify(resultPublisher).publishFailure(eq("uuid-1"), eq(7L), eq(100L), anyString(), anyInt());
        verify(resultPublisher, never())
                .publishSuccess(anyString(), anyLong(), any(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("a job with no jobId is rejected to the DLQ and never claims a row")
    void rejectsMissingIdempotencyKey() {
        QrGenerationJob message = new QrGenerationJob(
                null, 7L, 1L, null, null, 100L, 9L, "Someone", "a@example.com",
                "DAILY", null, null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5),
                null, "subject", "greeting", LocalDateTime.now());

        assertThatThrownBy(() -> listener.onGenerationJob(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("jobId");

        verify(jobService, never()).claim(any());
    }

    @Test
    @DisplayName("a job with a null campusId is rejected by the DTO's own constraints")
    void rejectsInvalidPayload() {
        QrGenerationJob message = new QrGenerationJob(
                "uuid-2", 7L, null, null, null, 100L, 9L, "Someone", "a@example.com",
                "DAILY", null, null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5),
                null, "subject", "greeting", LocalDateTime.now());

        assertThatThrownBy(() -> listener.onGenerationJob(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("campusId");

        verify(jobService, never()).claim(any());
    }

    @Test
    @DisplayName("a retry reuses the QR from the earlier attempt and reports attempt 2")
    void retryReusesExistingRecord() {
        QrGenerationJob message = job("uuid-3", 100L);
        GenerationJob retried = row(11L, 1);

        when(jobService.claim(message)).thenReturn(retried);
        when(qrRecordService.findActiveByPassId(7L)).thenReturn(Optional.of(record()));

        listener.onGenerationJob(message);

        verify(qrRecordService, never()).generate(any());
        verify(resultPublisher).publishSuccess("uuid-3", 7L, 100L,
                "1/qr/7/3.png", "1/pdf/7/3.pdf", 2);
    }

    @Test
    @DisplayName("a first attempt never consults the reuse path")
    void firstAttemptAlwaysGenerates() {
        QrGenerationJob message = job("uuid-4", null);
        GenerationJob first = row(13L, 0);

        when(jobService.claim(message)).thenReturn(first);
        when(qrRecordService.generate(any())).thenReturn(generated());

        listener.onGenerationJob(message);

        verify(qrRecordService, never()).findActiveByPassId(anyLong());
        verify(qrRecordService).generate(any());
    }

    @Test
    @DisplayName("a permanent failure marks FAILED, publishes failure, and skips retries")
    void permanentFailureGoesStraightToDlq() {
        QrGenerationJob message = job("uuid-5", 100L);
        GenerationJob row = row(12L, 0);

        when(jobService.claim(message)).thenReturn(row);
        doThrow(new PermanentGenerationException("token hash collision"))
                .when(qrRecordService).generate(any());

        assertThatThrownBy(() -> listener.onGenerationJob(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(jobService).markFailed(eq(12L), anyString());
        verify(resultPublisher).publishFailure(eq("uuid-5"), eq(7L), eq(100L), anyString(), eq(1));
        verify(jobService, never()).recordAttempt(anyLong(), anyString());
        verify(jobService, never()).markDone(anyLong());
    }

    @Test
    @DisplayName("a transient failure records the attempt and publishes NOTHING")
    void transientFailureDoesNotReportToGatepass() {
        QrGenerationJob message = job("uuid-6", 100L);
        GenerationJob row = row(14L, 0);

        when(jobService.claim(message)).thenReturn(row);
        doThrow(new IllegalStateException("storage unreachable"))
                .when(qrRecordService).generate(any());

        assertThatThrownBy(() -> listener.onGenerationJob(message))
                .isInstanceOf(IllegalStateException.class);

        verify(jobService).recordAttempt(eq(14L), anyString());
        // More attempts are coming. Telling gatepass "failed" now would make it
        // give up on a pass that is about to succeed.
        verify(resultPublisher, never())
                .publishFailure(anyString(), anyLong(), any(), anyString(), anyInt());
        verify(resultPublisher, never())
                .publishSuccess(anyString(), anyLong(), any(), anyString(), anyString(), anyInt());
    }

    // ---------- fixtures ----------

    private QrGenerationJob job(String jobId, Long batchId) {
        return new QrGenerationJob(
                jobId, 7L, 1L, "Demo Campus", "DEMO", batchId,
                9L, "Someone", "a@example.com",
                "DAILY", null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5),
                null, "subject", "greeting", LocalDateTime.now());
    }

    private GenerationJob row(Long id, int retryCount) {
        return GenerationJob.builder()
                .id(id)
                .passId(7L)
                .campusId(1L)
                .status(JobStatus.PROCESSING)
                .retryCount(retryCount)
                .build();
    }

    private QrRecordResponse record() {
        return QrRecordResponse.builder()
                .passId(7L)
                .campusId(1L)
                .qrKey("1/qr/7/3.png")
                .pdfKey("1/pdf/7/3.pdf")
                .validFrom(LocalDate.of(2026, 8, 1))
                .validTo(LocalDate.of(2026, 8, 5))
                .active(true)
                .build();
    }

    private QrRecordService.GeneratedToken generated() {
        return new QrRecordService.GeneratedToken("plain-token", record());
    }
}
