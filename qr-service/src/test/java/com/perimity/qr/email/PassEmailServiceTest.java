package com.perimity.qr.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perimity.qr.entity.enums.EmailStatus;
import com.perimity.qr.messaging.contract.QrGenerationJob;
import com.perimity.qr.service.GenerationJobService;
import com.perimity.qr.storage.StorageService;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * The branches here all share one property: getting them wrong produces no
 * error anywhere. A pass is generated, a job says DONE, and a person simply
 * never hears about it - or hears about it twice.
 */
class PassEmailServiceTest {

    private EmailSender emailSender;
    private StorageService storageService;
    private GenerationJobService jobService;
    private PassEmailService service;

    @BeforeEach
    void setUp() {
        emailSender = Mockito.mock(EmailSender.class);
        storageService = Mockito.mock(StorageService.class);
        jobService = Mockito.mock(GenerationJobService.class);
        service = new PassEmailService(emailSender, storageService, jobService);
    }

    @Test
    @DisplayName("sends the PDF with gatepass-service's own subject and greeting")
    void sendsWithProducerWording() {
        when(jobService.emailStatusOf(1L)).thenReturn(EmailStatus.PENDING);
        when(storageService.get("1/pdf/7/3.pdf")).thenReturn(new byte[]{1, 2, 3});

        service.sendPassEmail(1L, job("visitor@example.com"), "1/pdf/7/3.pdf");

        ArgumentCaptor<PassEmail> sent = ArgumentCaptor.forClass(PassEmail.class);
        verify(emailSender).send(sent.capture());

        assertThat(sent.getValue().to()).isEqualTo("visitor@example.com");
        assertThat(sent.getValue().subject()).isEqualTo("Your gate pass for Open Day");
        assertThat(sent.getValue().body()).contains("Hi Someone");
        assertThat(sent.getValue().pdf()).hasSize(3);

        verify(jobService).markEmailSent(1L);
    }

    @Test
    @DisplayName("a redelivery of an already-sent job does not send a second email")
    void doesNotResendOnRedelivery() {
        when(jobService.emailStatusOf(1L)).thenReturn(EmailStatus.SENT);

        service.sendPassEmail(1L, job("visitor@example.com"), "1/pdf/7/3.pdf");

        // The whole point - a visitor must not receive two emails with two QRs.
        verify(emailSender, never()).send(any());
        verify(storageService, never()).get(anyString());
        verify(jobService, never()).markEmailSent(anyLong());
    }

    @Test
    @DisplayName("no recipient is recorded as NO_RECIPIENT, not as a failure")
    void noRecipientIsNotAFailure() {
        when(jobService.emailStatusOf(1L)).thenReturn(EmailStatus.PENDING);

        service.sendPassEmail(1L, job(null), "1/pdf/7/3.pdf");

        verify(jobService).markEmailNotRequired(1L);
        verify(jobService, never()).markEmailFailed(anyLong(), anyString());
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("a malformed address fails immediately without troubling the mail server")
    void malformedAddressFailsFast() {
        when(jobService.emailStatusOf(1L)).thenReturn(EmailStatus.PENDING);

        service.sendPassEmail(1L, job("not-an-address"), "1/pdf/7/3.pdf");

        verify(jobService).markEmailFailed(eq(1L), anyString());
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("a mail server failure is recorded and never rethrown")
    void deliveryFailureIsSwallowedAndRecorded() {
        when(jobService.emailStatusOf(1L)).thenReturn(EmailStatus.PENDING);
        when(storageService.get(anyString())).thenReturn(new byte[]{1});
        doThrow(new EmailDeliveryException("connection refused"))
                .when(emailSender).send(any());

        // No assertThatThrownBy: the absence of an exception IS the assertion.
        // Rethrowing would retry the generation job and mint a second token to
        // repair a mail problem.
        service.sendPassEmail(1L, job("visitor@example.com"), "1/pdf/7/3.pdf");

        verify(jobService).markEmailFailed(eq(1L), anyString());
        verify(jobService, never()).markEmailSent(anyLong());
    }

    @Test
    @DisplayName("a missing PDF in storage is recorded, not thrown at the consumer")
    void missingPdfIsRecorded() {
        when(jobService.emailStatusOf(1L)).thenReturn(EmailStatus.PENDING);
        when(storageService.get(anyString()))
                .thenThrow(new EntityNotFoundException("No stored object"));

        service.sendPassEmail(1L, job("visitor@example.com"), "1/pdf/7/3.pdf");

        verify(jobService).markEmailFailed(eq(1L), anyString());
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("missing wording falls back to neutral copy rather than sending nulls")
    void fallsBackWhenProducerSentNoWording() {
        when(jobService.emailStatusOf(1L)).thenReturn(EmailStatus.PENDING);
        when(storageService.get(anyString())).thenReturn(new byte[]{1});

        QrGenerationJob noCopy = new QrGenerationJob(
                "uuid-1", 7L, 1L, null, null, null, 9L, "Someone", "visitor@example.com",
                null,
                "DAILY", null, null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5),
                null, null, null, LocalDateTime.now());

        service.sendPassEmail(1L, noCopy, "1/pdf/7/3.pdf");

        ArgumentCaptor<PassEmail> sent = ArgumentCaptor.forClass(PassEmail.class);
        verify(emailSender).send(sent.capture());

        assertThat(sent.getValue().subject()).isNotBlank();
        assertThat(sent.getValue().body()).isNotBlank();
        verify(jobService).markEmailSent(1L);
    }

    private QrGenerationJob job(String email) {
        return new QrGenerationJob(
                "uuid-1", 7L, 1L, "Demo Campus", "DEMO", null,
                9L, "Someone", email,
                "Computer Science",
                "EVENT", 3L, "Open Day",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5),
                null,
                "Your gate pass for Open Day",
                "Hi Someone,\n\nWelcome to Open Day on 2026-08-01 to 2026-08-05.",
                LocalDateTime.now());
    }
}
