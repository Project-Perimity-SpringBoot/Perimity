package com.perimity.qr.config;

import com.perimity.qr.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * The sixth SecurityConfig. It should have been the first.
 *
 * ======================================================================
 *  WHAT THIS FIXES
 * ======================================================================
 * qr-service shipped with spring-boot-starter-web and no
 * spring-boot-starter-security at all - the only one of the six without it.
 * InternalApiKeyFilter guarded /api/qr/internal/ and said so honestly in its
 * own javadoc: "the public read endpoints, ping and Swagger are all skipped
 * here and will be covered by the shared JWT filter". That filter was never
 * added, so "skipped here" meant skipped entirely.
 *
 * The result was that
 *
 *     GET /api/qr/{passId}
 *
 * answered anybody, with no token, returning the object-storage keys for a
 * pass's QR image and PDF plus its campus and validity window - for any pass id
 * you cared to count through. On a product whose entire claim is a
 * forgery-proof pass, an unauthenticated enumeration of every pass in the
 * platform is the wrong hole to leave open.
 *
 * Nothing about the frontend changes. It already attaches the Bearer token to
 * all six axios clients, including this one; the header was simply being
 * ignored.
 *
 * ======================================================================
 *  WHY /api/qr/internal/** IS permitAll HERE AND STILL NOT PUBLIC
 * ======================================================================
 * Those paths are guarded by InternalApiKeyFilter, which checks the shared
 * X-Internal-Api-Key in constant time. A calling service has no user and no JWT
 * to present - gatepass invalidating a pass is not a person - so requiring one
 * would mean minting a service account, which is a login that never expires and
 * never gets rotated. Strictly worse than a shared key in .env.
 *
 * permitAll means "Spring Security does not decide this one", not "anyone may
 * call it". The API key filter is auto-registered as a plain servlet filter and
 * therefore runs AFTER this chain, so it still refuses every unkeyed request.
 * Its own comment anticipated exactly this: adding starter-security later must
 * not silently change the behaviour of those endpoints, and it does not.
 *
 * ======================================================================
 *  WHAT IS STILL NOT ENFORCED, STATED PLAINLY
 * ======================================================================
 * GET /api/qr/{passId} is authenticated but NOT ownership-scoped: a signed-in
 * student can still read another holder's QR keys by changing the id.
 *
 * GET /api/qr/{passId}/pdf USED TO SHARE THAT GAP AND NO LONGER DOES. It was
 * the worse of the two by a distance - not keys, but the pass itself, a PDF
 * whose QR opens a gate - and it was confirmed exploitable: one signed-in
 * student pulled another holder's pass and that pass scanned ALLOWED. It is
 * now owner-or-staff, enforced in QrRecordService.download.
 *
 * The fix did not need the cross-service call this comment once assumed.
 * QrGenerationJob already carried holderUserId and the listener was dropping
 * it, so the holder is persisted on QrRecord at generation time and the read
 * path stays local. The same approach would close /api/qr/{passId}.
 *
 * Note the two are NOT equivalent in risk: the metadata endpoint leaks storage
 * keys, which are useless without this service handing over the object.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final CorsConfigurationSource corsSource;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, CorsConfigurationSource corsSource) {
        this.jwtFilter = jwtFilter;
        this.corsSource = corsSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .exceptionHandling(e -> e
                    .authenticationEntryPoint((req, res, ex) -> {
                        res.setStatus(401);
                        res.setContentType("application/json");
                        res.getWriter().write("{\"success\":false,\"message\":"
                                + "\"Authentication required\",\"data\":null,\"errors\":[]}");
                    })
                    .accessDeniedHandler((req, res, ex) -> {
                        res.setStatus(403);
                        res.setContentType("application/json");
                        res.getWriter().write("{\"success\":false,\"message\":"
                                + "\"Your role is not permitted to perform this action\","
                                + "\"data\":null,\"errors\":[]}");
                    }))
            .authorizeHttpRequests(auth -> auth

                    // ---- The paths that must be public in every service ----
                    .requestMatchers("/api/qr/ping").permitAll()

                    // Spring Boot runs this filter chain on ERROR dispatches as
                    // well as REQUEST ones. Without this line an unhandled 500 is
                    // re-dispatched to /error with no credentials attached, and
                    // anyRequest().authenticated() reports it as
                    // 401 "Authentication required" - hiding the real fault.
                    //
                    // Found in user-service, where a broken database column
                    // surfaced as a 401 on an internal endpoint whose API key was
                    // correct all along. All six services had it.
                    //
                    // Safe: Spring Boot's server.error.include-message and
                    // include-stacktrace are both off by default.
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                     "/api-docs", "/api-docs/**",
                                     "/v3/api-docs", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                    // ---- Service-to-service. Guarded by InternalApiKeyFilter,
                    //      which runs after this chain. See the class javadoc. ----
                    .requestMatchers("/api/qr/internal/**").permitAll()

                    // ---- Bulk generation progress. This is the second of the two
                    //      bars on the faculty bulk screen - gatepass reports rows
                    //      processed, this reports passes generated and emails
                    //      delivered. Whoever can run a batch can watch it. ----
                    .requestMatchers(HttpMethod.GET, "/api/qr/jobs/**")
                            .hasAnyRole("FACULTY", "CAMPUS_ADMIN", "SUPER_ADMIN")

                    // ---- Everything else, which today means GET /api/qr/{passId}.
                    //      Declared last because /api/qr/* would otherwise swallow
                    //      /api/qr/ping and /api/qr/jobs above it. ----
                    .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
