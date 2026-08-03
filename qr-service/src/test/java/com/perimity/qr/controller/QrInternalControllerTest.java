package com.perimity.qr.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.perimity.qr.dto.QrDecryptRequest;
import com.perimity.qr.dto.QrDecryptResponse;
import com.perimity.qr.dto.QrInvalidateRequest;
import com.perimity.qr.dto.QrRecordResponse;
import com.perimity.qr.dto.ResendEmailRequest;
import com.perimity.qr.dto.UndeliveredEmailResponse;
import com.perimity.qr.email.PassEmailRetryService;
import com.perimity.qr.entity.enums.EmailStatus;
import com.perimity.qr.security.JwtService;
import com.perimity.qr.service.QrDecryptService;
import com.perimity.qr.service.QrRecordService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for the service-to-service endpoints.
 *
 * These matter more than their obscurity suggests. /decrypt is the scan path -
 * guard-service calls it for every person who walks through a gate - and until
 * now nothing exercised it above the service layer, so a missing @Valid or a
 * changed status code would have reached a gate before it reached a test.
 *
 * addFilters = false strips both the security chain and InternalApiKeyFilter.
 * These tests are about request mapping, validation and status codes; the key
 * check has its own class, InternalApiKeyFilterTest, where the filter is left
 * on deliberately.
 */
@WebMvcTest(QrInternalController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "qr.internal.api-key=test-key")
class QrInternalControllerTest {

    /** 24 chars minimum, URL-safe Base64 - see ValidationPatterns.QR_TOKEN. */
    private static final String VALID_TOKEN = "abcdefghijklmnopqrstuvwxyz012345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QrRecordService qrRecordService;

    @MockBean
    private PassEmailRetryService passEmailRetryService;

    @MockBean
    private QrDecryptService qrDecryptService;

    @MockBean
    private JwtService jwtService;

    // ---------------------------------------------------------------
    // POST /decrypt - the scan path
    // ---------------------------------------------------------------

    @Test
    void decrypt_returnsTheServiceVerdict() throws Exception {
        given(qrDecryptService.decrypt(any())).willReturn(
                QrDecryptResponse.builder()
                        .tokenValid(true).passId(41L).campusId(2L)
                        .withinValidityWindow(true).build());

        mockMvc.perform(post("/api/qr/internal/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                QrDecryptRequest.builder().token(VALID_TOKEN).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Token is valid"))
                .andExpect(jsonPath("$.data.tokenValid").value(true))
                .andExpect(jsonPath("$.data.passId").value(41));
    }

    /**
     * The single most important assertion in this file.
     *
     * A refused token is a successful ANSWER, not a failed request. If this ever
     * returns 4xx, guard-service's call wrapper treats every forged scan as an
     * outage - and its own comment says what that costs: "a timeout means we do
     * not know whether the pass is valid", which must never become a red card.
     */
    @Test
    void decrypt_refusedTokenIsStill200() throws Exception {
        given(qrDecryptService.decrypt(any())).willReturn(
                QrDecryptResponse.builder()
                        .tokenValid(false).reason("SUPERSEDED").build());

        mockMvc.perform(post("/api/qr/internal/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                QrDecryptRequest.builder().token(VALID_TOKEN).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Token refused"))
                .andExpect(jsonPath("$.data.tokenValid").value(false));
    }

    /**
     * Proves @Valid is on the parameter. Without it every constraint on
     * QrDecryptRequest is skipped and this endpoint hands arbitrary user input
     * straight to a cipher - the one place in this service where that matters
     * most. The service must never be reached.
     */
    @Test
    void decrypt_rejectsAMalformedTokenWithoutCallingTheCipher() throws Exception {
        mockMvc.perform(post("/api/qr/internal/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                QrDecryptRequest.builder().token("not a token!!").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(qrDecryptService);
    }

    @Test
    void decrypt_rejectsABlankToken() throws Exception {
        mockMvc.perform(post("/api/qr/internal/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                QrDecryptRequest.builder().token("").build())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(qrDecryptService);
    }

    // ---------------------------------------------------------------
    // POST /invalidate/{passId}
    // ---------------------------------------------------------------

    @Test
    void invalidate_returnsTheRetiredRecord() throws Exception {
        given(qrRecordService.invalidate(eq(41L), any())).willReturn(
                QrRecordResponse.builder().passId(41L).campusId(2L).active(false).build());

        mockMvc.perform(post("/api/qr/internal/invalidate/41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                QrInvalidateRequest.builder().reason("Superseded by re-issue").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("QR invalidated"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    /**
     * The reason is written to the audit trail. A blank one would leave a record
     * saying a pass was retired and nothing about why.
     */
    @Test
    void invalidate_requiresAReason() throws Exception {
        mockMvc.perform(post("/api/qr/internal/invalidate/41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                QrInvalidateRequest.builder().reason("  ").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(qrRecordService);
    }

    @Test
    void invalidate_rejectsANonPositivePassId() throws Exception {
        mockMvc.perform(post("/api/qr/internal/invalidate/-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                QrInvalidateRequest.builder().reason("Revoked").build())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(qrRecordService);
    }

    // ---------------------------------------------------------------
    // POST /{passId}/resend-email
    // ---------------------------------------------------------------

    @Test
    void resendEmail_reportsTheResultingStatus() throws Exception {
        given(passEmailRetryService.resend(eq(41L), anyString(), any(), any()))
                .willReturn(EmailStatus.SENT);

        mockMvc.perform(post("/api/qr/internal/41/resend-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ResendEmailRequest.builder()
                                        .email("holder@example.com").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailStatus").value("SENT"));

        verify(passEmailRetryService).resend(eq(41L), eq("holder@example.com"), any(), any());
    }

    /**
     * A holder who could reach this endpoint could post a pass PDF to any
     * address they chose. The address must at least be an address.
     */
    @Test
    void resendEmail_rejectsAMalformedAddress() throws Exception {
        mockMvc.perform(post("/api/qr/internal/41/resend-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ResendEmailRequest.builder().email("not-an-email").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(passEmailRetryService);
    }

    // ---------------------------------------------------------------
    // GET /emails/undelivered
    // ---------------------------------------------------------------

    /**
     * Returns pass ids, never email addresses - gatepass-service holds those.
     * The assertion on the absent field is the point: if someone widens
     * UndeliveredEmailResponse to carry the recipient, this fails.
     */
    @Test
    void undelivered_returnsPassIdsAndNoAddresses() throws Exception {
        given(passEmailRetryService.undelivered()).willReturn(List.of(
                UndeliveredEmailResponse.builder()
                        .jobId(9L).passId(41L).batchId(3L)
                        .emailStatus(EmailStatus.FAILED).emailError("connect timed out")
                        .build()));

        mockMvc.perform(get("/api/qr/internal/emails/undelivered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("1 pass email(s) undelivered"))
                .andExpect(jsonPath("$.data[0].passId").value(41))
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].recipient").doesNotExist());
    }

    @Test
    void undelivered_emptyListStillReports200() throws Exception {
        given(passEmailRetryService.undelivered()).willReturn(List.of());

        mockMvc.perform(get("/api/qr/internal/emails/undelivered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("0 pass email(s) undelivered"))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
