package com.perimity.guard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.perimity.guard.config.SecurityConfig;
import com.perimity.guard.document.enums.ScanResult;
import com.perimity.guard.dto.request.ScanRequestDto;
import com.perimity.guard.dto.response.ScanResponse;
import com.perimity.guard.security.CurrentUser;
import com.perimity.guard.security.JwtAuthenticationFilter;
import com.perimity.guard.security.JwtService;
import com.perimity.guard.security.PerimityPrincipal;
import com.perimity.guard.security.Role;
import com.perimity.guard.service.ScanService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Day 7 gate, asserted rather than assumed.
 *
 * ==========================================================================
 * THE TEST THAT MATTERS MOST IS bodyCannotOverrideTheGuardId
 * ==========================================================================
 * guardUserId used to arrive in the request body. Any caller could post a scan
 * as any guard, at any gate, and the register recorded it as fact. That is fixed,
 * and this is the test that keeps it fixed - because the tempting "helpful"
 * change six months from now is to accept a guardUserId again so the scanner app
 * can specify one.
 *
 * Note that it does not merely check the response. It captures what reached
 * ScanService and asserts the id came from the principal, because a body field
 * that is silently ignored and a body field that is honoured look identical from
 * the outside until the day they do not.
 *
 * ==========================================================================
 * WHY EXPLICIT @Import RATHER THAN @SpringBootTest
 * ==========================================================================
 * @WebMvcTest does not pick up ordinary @Component classes, so SecurityConfig's
 * filter chain would be absent and every request would sail through with a 200 -
 * a green suite proving nothing. The four security classes are imported by name.
 *
 * BUT it DOES pick up jakarta.servlet.Filter beans, whether you import them or
 * not: filters are part of the web layer, so Boot's WebMvcTypeExcludeFilter lets
 * them through while excluding services and repositories. That means
 * InternalApiKeyFilter is instantiated here even though nothing on this
 * controller lives under /api/internal/**, and its constructor fails fast when
 * the key is missing.
 *
 * Hence BOTH properties below. Supplying the key is also the more honest test:
 * the filter really is in the chain in production, and its shouldNotFilter skips
 * every path this class exercises - so its presence changes nothing except that
 * the context now starts.
 *
 * Both values are throwaways. The jwt secret only needs 32+ characters for
 * JwtService's constructor; no token is ever signed with it, because these tests
 * inject an Authentication directly and bypass the filter entirely.
 */
@WebMvcTest(ScanController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CurrentUser.class})
@TestPropertySource(properties = {
        "perimity.jwt.secret=test-only-secret-value-at-least-32-chars-long",
        "perimity.internal.api-key=test-only-internal-key"
})
class ScanControllerSecurityTest {

    private static final Long GUARD_ID = 55L;
    private static final String BODY = """
            {"token":"dev:118:108:R_Kulkarni:1:DAILY::2026-07-01:2026-12-31"}
            """;

    @Autowired private MockMvc mockMvc;

    @MockBean private ScanService scanService;

    @Test
    @DisplayName("no token is 401 - bring a token, not 'you are not allowed'")
    void noTokenIsUnauthorised() throws Exception {
        mockMvc.perform(post("/api/guard/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        // The request must not reach the gate logic at all.
        verify(scanService, never()).scan(any(), any());
    }

    @Test
    @DisplayName("a STUDENT token is 403 - we know who you are, and it is not enough")
    void wrongRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/api/guard/scan")
                        .with(authentication(principal(Role.STUDENT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());

        verify(scanService, never()).scan(any(), any());
    }

    @Test
    @DisplayName("a CAMPUS_ADMIN token is also 403 - only a guard on shift may scan")
    void adminCannotScan() throws Exception {
        // An admin scanning would produce an entry log with no shift behind it,
        // so the gate is GUARD-only rather than "GUARD or above".
        mockMvc.perform(post("/api/guard/scan")
                        .with(authentication(principal(Role.CAMPUS_ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a GUARD token reaches the gate logic")
    void guardIsAllowedThrough() throws Exception {
        when(scanService.scan(any(), eq(GUARD_ID))).thenReturn(allowedResponse());

        mockMvc.perform(post("/api/guard/scan")
                        .with(authentication(principal(Role.GUARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());

        verify(scanService).scan(any(), eq(GUARD_ID));
    }

    @Test
    @DisplayName("a guardUserId in the body cannot override the one in the token")
    void bodyCannotOverrideTheGuardId() throws Exception {
        when(scanService.scan(any(), any())).thenReturn(allowedResponse());

        // 999 is a different guard. Before Day 7 this would have been honoured.
        String bodyClaimingAnotherGuard = """
                {"token":"dev:118:108:R_Kulkarni:1:DAILY::2026-07-01:2026-12-31",
                 "guardUserId":999}
                """;

        mockMvc.perform(post("/api/guard/scan")
                        .with(authentication(principal(Role.GUARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyClaimingAnotherGuard))
                .andExpect(status().isOk());

        ArgumentCaptor<Long> guardId = ArgumentCaptor.forClass(Long.class);
        verify(scanService).scan(any(ScanRequestDto.class), guardId.capture());

        // The token said 55. The body said 999. 55 wins, and it is not close.
        assertThat(guardId.getValue()).isEqualTo(GUARD_ID);
        assertThat(guardId.getValue()).isNotEqualTo(999L);
    }

    @Test
    @DisplayName("a malformed body is 400 in ApiResponse shape, not a stack trace")
    void malformedBodyIsBadRequest() throws Exception {
        // Empty token violates @NotBlank. Without @Valid on the controller
        // parameter none of the ~240 constraints in this service run at all, so
        // this asserts the wiring as much as the rule.
        mockMvc.perform(post("/api/guard/scan")
                        .with(authentication(principal(Role.GUARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(scanService, never()).scan(any(), any());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * An Authentication shaped exactly as JwtAuthenticationFilter builds one:
     * a PerimityPrincipal plus a single ROLE_ authority. Building it here rather
     * than signing a JWT keeps these tests about authorisation, not about token
     * parsing - which JwtService's own tests should cover.
     */
    private Authentication principal(Role role) {
        PerimityPrincipal principal = new PerimityPrincipal(
                GUARD_ID, "guard@example.com", "A Guard", role, 1L);

        return new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private ScanResponse allowedResponse() {
        return new ScanResponse(ScanResult.ALLOWED, "Welcome, R. Kulkarni", null,
                118L, 108L, "R. Kulkarni", null, null, 3L, "Main Gate",
                LocalDateTime.now(), "entrylog-1", null);
    }
}
