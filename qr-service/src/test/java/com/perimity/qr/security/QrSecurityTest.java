package com.perimity.qr.security;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.perimity.qr.config.QrCorsConfig;
import com.perimity.qr.config.SecurityConfig;
import com.perimity.qr.controller.PingController;
import com.perimity.qr.controller.QrController;
import com.perimity.qr.dto.QrRecordResponse;
import com.perimity.qr.service.GenerationJobService;
import com.perimity.qr.service.QrRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the B-HIGH defect is actually closed.
 *
 * Before SecurityConfig existed, GET /api/qr/{passId} answered anybody with no
 * token at all - the object-storage keys, campus and validity window for any
 * pass id you cared to count through. A test that only checks the happy path
 * would have passed just as cheerfully then as it does now, which is why the
 * first assertion here is the unauthenticated one.
 *
 * Unlike QrControllerTest, the filter chain is deliberately left ON. That is
 * the entire point of this class.
 */
@WebMvcTest({QrController.class, PingController.class})
@Import({SecurityConfig.class, QrCorsConfig.class})
@TestPropertySource(properties = "qr.internal.api-key=test-key-not-used-by-these-tests")
class QrSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QrRecordService qrRecordService;

    @MockBean
    private GenerationJobService generationJobService;

    /**
     * The real JwtService needs perimity.jwt.secret and would refuse to start
     * without it. Mocked because these tests drive the security context through
     * @WithMockUser rather than through a signed token - token parsing itself is
     * auth-service's contract, not qr-service's.
     */
    @MockBean
    private JwtService jwtService;

    // ---------------------------------------------------------------
    // The defect itself
    // ---------------------------------------------------------------

    @Test
    void passLookup_withoutAToken_is401() throws Exception {
        mockMvc.perform(get("/api/qr/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    /**
     * The pass PDF is the entry credential in document form. If this endpoint
     * ever answers without a token, anyone can download any holder's pass by
     * counting through ids - worse than the metadata leak, because a PDF is
     * immediately usable at a gate.
     */
    @Test
    void pdfDownload_withoutAToken_is401() throws Exception {
        mockMvc.perform(get("/api/qr/1/pdf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void qrImage_withoutAToken_is401() throws Exception {
        mockMvc.perform(get("/api/qr/1/image"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void passLookup_withAToken_isAllowed() throws Exception {
        given(qrRecordService.getActiveByPassId(1L)).willReturn(
                QrRecordResponse.builder().passId(1L).campusId(2L).active(true).build());

        mockMvc.perform(get("/api/qr/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passId").value(1));
    }

    // ---------------------------------------------------------------
    // Role rules on the bulk-progress endpoints
    // ---------------------------------------------------------------

    /**
     * A student holds a pass; they do not run bulk batches. If this ever starts
     * returning 200, a pass holder can read platform-wide generation counts.
     */
    @Test
    @WithMockUser(roles = "STUDENT")
    void batchProgress_isForbiddenForAStudent() throws Exception {
        mockMvc.perform(get("/api/qr/jobs/batch/3/progress"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Your role is not permitted to perform this action"));
    }

    @Test
    @WithMockUser(roles = "FACULTY")
    void batchProgress_isAllowedForFaculty() throws Exception {
        given(generationJobService.getBatchProgress(3L)).willReturn(null);

        mockMvc.perform(get("/api/qr/jobs/batch/3/progress"))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // Paths that must stay reachable
    // ---------------------------------------------------------------

    /**
     * Locking these by accident is the classic over-correction: the service
     * disappears from Eureka and Swagger 401s, which reads as "qr-service is
     * down" rather than "someone tightened a matcher".
     */
    @Test
    void ping_staysPublic() throws Exception {
        mockMvc.perform(get("/api/qr/ping")).andExpect(status().isOk());
    }

    /**
     * 404, not 200: springdoc is not auto-configured in a sliced @WebMvcTest, so
     * the path does not exist here. That is the assertion - a 404 proves the
     * request reached the dispatcher, which it could only do by passing the
     * security chain. A 401 would mean the matcher had been lost.
     */
    @Test
    void swagger_isNotBlockedBySecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    /**
     * permitAll here means "Spring Security does not decide this one", NOT
     * "anyone may call it".
     *
     * Both filters answer 401, so the status alone proves nothing. The message
     * is what identifies the responder: InternalApiKeyFilter writes "Internal
     * authentication required", SecurityConfig's entry point writes
     * "Authentication required". Asserting the former proves the request passed
     * the security chain and was refused by the API key filter behind it -
     * exactly the arrangement InternalApiKeyFilter's javadoc promised would
     * survive adding starter-security.
     */
    @Test
    void internalPaths_areRefusedByTheApiKeyFilterNotBySpringSecurity() throws Exception {
        mockMvc.perform(get("/api/qr/internal/emails/undelivered"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Internal authentication required"));
    }
}
