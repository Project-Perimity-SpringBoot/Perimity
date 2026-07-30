package com.perimity.gatepass.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.perimity.gatepass.bulk.TemplateWriter;
import com.perimity.gatepass.dto.request.BulkConfirmDto;
import com.perimity.gatepass.dto.response.BulkUploadBatchResponse;
import com.perimity.gatepass.entity.enums.BatchStatus;
import com.perimity.gatepass.entity.enums.PassType;
import com.perimity.gatepass.security.CurrentUser;
import com.perimity.gatepass.service.BulkUploadService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ==========================================================================
 *  THE REASON THIS TEST CLASS EXISTS, ABOVE ALL OTHERS.
 * ==========================================================================
 *
 * A controller parameter missing @Valid does not fail. It does not warn. It
 * does not log. Spring simply never runs the constraints, and every single one
 * of the ~240 annotations in this service becomes decoration. The endpoint
 * accepts anything and the first sign of trouble is a database constraint
 * violation surfacing as a 500 - or worse, no error at all and bad data landing
 * in gatepassdb.
 *
 * No other kind of test catches this. A unit test of the service passes a
 * well-formed object by hand. An integration test that only sends valid input
 * never notices. It has to be a request through the real MVC stack carrying
 * input that SHOULD be rejected, asserting that it IS.
 *
 * SECURITY IS DISABLED HERE ON PURPOSE (addFilters = false). These tests are
 * about validation wiring, not authorisation. Leaving the filter chain in means
 * every test needs a JWT and a 401 masks the thing being tested. Role checks
 * belong in their own test, against the SecurityConfig.
 */
@ControllerTest(BulkUploadController.class)
@DisplayName("BulkUploadController - validation wiring")
class BulkUploadControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private BulkUploadService service;

    @MockBean
    private TemplateWriter templateWriter;

    @MockBean
    private CurrentUser currentUser;

    @BeforeEach
    void stubTheToken() {
        when(currentUser.campusId()).thenReturn(1L);
        when(currentUser.userId()).thenReturn(42L);
    }

    // ------------------------------------------------------------- confirm

    @Test
    @DisplayName("confirm rejects a body with confirmed=false and never reaches the service")
    void confirmRejectsFalse() throws Exception {
        BulkConfirmDto dto = BulkConfirmDto.builder()
                .confirmedBy(42L)
                .confirmed(false)          // @AssertTrue must reject this
                .build();

        mvc.perform(post("/api/gatepass/bulk/1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        // The important half of the assertion. A 400 that still called the
        // service would mean the guard fired too late to stop 600 passes.
        verify(service, never()).confirm(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("confirm rejects a body with confirmed missing entirely")
    void confirmRejectsNull() throws Exception {
        mvc.perform(post("/api/gatepass/bulk/1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedBy\":42}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).confirm(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("confirm accepts confirmed=true and overwrites confirmedBy from the token")
    void confirmAcceptsTrue() throws Exception {
        when(service.confirm(eq(1L), eq(1L), any())).thenReturn(aBatch());

        mvc.perform(post("/api/gatepass/bulk/1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        // A caller claiming to be user 9999 - the controller
                        // must ignore this and use the token's 42.
                        .content("{\"confirmedBy\":9999,\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    // -------------------------------------------------------------- upload

    @Test
    @DisplayName("validate rejects a request with no file at all")
    void validateRejectsMissingFile() throws Exception {
        mvc.perform(multipart("/api/gatepass/bulk/validate")
                        .param("passType", "EVENT")
                        .param("eventId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("validate rejects a passType that is not DAILY or EVENT")
    void validateRejectsBadPassType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sheet.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{'P', 'K', 3, 4});

        mvc.perform(multipart("/api/gatepass/bulk/validate")
                        .file(file)
                        .param("passType", "MONTHLY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("validate passes campusId and uploadedBy from the token, not the request")
    void validateUsesTokenNotRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sheet.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{'P', 'K', 3, 4});

        mvc.perform(multipart("/api/gatepass/bulk/validate")
                        .file(file)
                        .param("passType", "DAILY")
                        // A caller trying to load rows into someone else's campus.
                        .param("campusId", "999")
                        .param("uploadedBy", "999"))
                .andExpect(status().isOk());

        // 1L and 42L come from the stubbed token. If these were 999 the
        // endpoint would be a cross-tenant write.
        verify(service).validate(any(), eq(1L), eq(42L), eq(PassType.DAILY), eq(null));
    }

    // ------------------------------------------------------------- helpers

    private BulkUploadBatchResponse aBatch() {
        return new BulkUploadBatchResponse(
                1L, 1L, 42L, PassType.EVENT, 1L,
                "campus-1/bulk/1/sheet.xlsx", "sheet.xlsx",
                BatchStatus.PROCESSING,
                10, 8, 2, 0, 0,
                null, null, null,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
