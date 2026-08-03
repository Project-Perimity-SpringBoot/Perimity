package com.perimity.qr.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.perimity.qr.config.QrCorsConfig;
import com.perimity.qr.config.SecurityConfig;
import com.perimity.qr.controller.QrInternalController;
import com.perimity.qr.email.PassEmailRetryService;
import com.perimity.qr.service.QrDecryptService;
import com.perimity.qr.service.QrRecordService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The shared-key gate on /api/qr/internal/**, with the filters left ON.
 *
 * QrInternalControllerTest strips the filters to test mapping and validation;
 * this class exists to prove the gate itself works. Both are needed - a test
 * suite that only ever runs with addFilters = false would stay green if the
 * filter stopped being registered at all.
 *
 * SecurityConfig is imported deliberately. Without it, Spring Boot's default
 * chain would answer every request first and this class would be testing
 * Boot's autoconfiguration rather than InternalApiKeyFilter.
 */
@WebMvcTest(QrInternalController.class)
@Import({SecurityConfig.class, QrCorsConfig.class})
@TestPropertySource(properties = "qr.internal.api-key=correct-horse-battery-staple")
class InternalApiKeyFilterTest {

    private static final String HEADER = "X-Internal-Api-Key";
    private static final String CORRECT_KEY = "correct-horse-battery-staple";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QrRecordService qrRecordService;

    @MockBean
    private PassEmailRetryService passEmailRetryService;

    @MockBean
    private QrDecryptService qrDecryptService;

    @MockBean
    private JwtService jwtService;

    @Test
    void noKey_is401AndNeverReachesTheController() throws Exception {
        mockMvc.perform(get("/api/qr/internal/emails/undelivered"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Internal authentication required"));

        verifyNoInteractions(passEmailRetryService);
    }

    @Test
    void wrongKey_is401() throws Exception {
        mockMvc.perform(get("/api/qr/internal/emails/undelivered")
                        .header(HEADER, "not-the-key"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(passEmailRetryService);
    }

    /**
     * A prefix of the real key must fail. MessageDigest.isEqual reads both
     * arrays fully, so a caller cannot recover the key one character at a time
     * by timing the response - String.equals would return early and leak how
     * many leading characters were right.
     */
    @Test
    void keyPrefix_is401() throws Exception {
        mockMvc.perform(get("/api/qr/internal/emails/undelivered")
                        .header(HEADER, CORRECT_KEY.substring(0, CORRECT_KEY.length() - 1)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(passEmailRetryService);
    }

    @Test
    void correctKey_passesThrough() throws Exception {
        given(passEmailRetryService.undelivered()).willReturn(List.of());

        mockMvc.perform(get("/api/qr/internal/emails/undelivered")
                        .header(HEADER, CORRECT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * The refusal must say nothing about which header was wrong or whether the
     * path exists. A caller probing for internal endpoints should learn nothing
     * from the difference between a bad key and a bad URL.
     */
    @Test
    void refusalLeaksNothingAboutTheKeyOrThePath() throws Exception {
        mockMvc.perform(get("/api/qr/internal/emails/undelivered")
                        .header(HEADER, "not-the-key"))
                .andExpect(jsonPath("$.message").value("Internal authentication required"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * shouldNotFilter skips everything outside the internal prefix. If that
     * prefix check ever broke, the API key would start being demanded on the
     * holder-facing endpoints too - which no browser sends, so every pass screen
     * would 401 at once.
     */
    @Test
    void nonInternalPaths_areNotAskedForTheKey() throws Exception {
        mockMvc.perform(get("/api/qr/ping"))
                .andExpect(status().isNotFound());
    }
}
