package com.perimity.gatepass.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.perimity.gatepass.dto.response.GatePassResponse;
import com.perimity.gatepass.entity.GatePass;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.entity.enums.PassType;
import com.perimity.gatepass.exception.ResourceNotFoundException;
import com.perimity.gatepass.service.GatePassService;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * THE ENDPOINT GUARD-SERVICE SCANS AGAINST.
 *
 * Palash's HttpPassVerificationClient calls this on every scan to confirm the
 * pass is still ACTIVE and still in date before the gate opens. It did not
 * exist until Day 11, so every scan was 404-ing or falling back to his stub.
 *
 * The contract he consumes is asserted field by field below. If someone renames
 * a field in GatePassResponse, this test fails here rather than at the gate
 * during a demo.
 */
@ControllerTest(GatePassInternalController.class)
@DisplayName("GatePassInternalController - the scan lookup")
class GatePassInternalControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GatePassService service;

    @Test
    @DisplayName("returns every field guard-service reads")
    void returnsTheScanContract() throws Exception {
        when(service.getForInternal(118L)).thenReturn(GatePassResponse.from(GatePass.builder()
                .id(118L)
                .holderUserId(500L)
                .holderName("Asha Menon")
                .campusId(3L)
                .passType(PassType.EVENT)
                .eventId(77L)
                .validFrom(LocalDate.of(2026, 8, 10))
                .validTo(LocalDate.of(2026, 8, 12))
                .status(PassStatus.ACTIVE)
                .build()));

        mvc.perform(get("/api/gatepass/internal/passes/118"))
                .andExpect(status().isOk())
                // Every one of these is read by HttpPassVerificationClient.
                .andExpect(jsonPath("$.data.id").value(118))
                .andExpect(jsonPath("$.data.holderUserId").value(500))
                .andExpect(jsonPath("$.data.holderName").value("Asha Menon"))
                .andExpect(jsonPath("$.data.campusId").value(3))
                .andExpect(jsonPath("$.data.passType").value("EVENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.eventId").value(77))
                .andExpect(jsonPath("$.data.validFrom").value("2026-08-10"))
                .andExpect(jsonPath("$.data.validTo").value("2026-08-12"));
    }

    @Test
    @DisplayName("a revoked pass still returns 200 - guard decides, not us")
    void revokedStillReturns200() throws Exception {
        // Important. A 404 for a revoked pass would be indistinguishable from
        // "no such pass", and guard-service needs to tell the guard WHY the
        // light is red. It maps status onto its own DenialReason.
        when(service.getForInternal(anyLong())).thenReturn(GatePassResponse.from(GatePass.builder()
                .id(9L).holderUserId(1L).holderName("Barred Person").campusId(1L)
                .passType(PassType.DAILY).validFrom(LocalDate.now())
                .status(PassStatus.REVOKED).build()));

        mvc.perform(get("/api/gatepass/internal/passes/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    @DisplayName("an unknown pass id is 404, which guard reads as an invalid token")
    void unknownPassIs404() throws Exception {
        when(service.getForInternal(404L))
                .thenThrow(ResourceNotFoundException.of("Pass", 404L));

        mvc.perform(get("/api/gatepass/internal/passes/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a non-positive id is rejected before it reaches the service")
    void rejectsZeroId() throws Exception {
        mvc.perform(get("/api/gatepass/internal/passes/0"))
                .andExpect(status().isBadRequest());
    }
}
