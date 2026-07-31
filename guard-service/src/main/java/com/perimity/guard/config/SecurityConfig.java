package com.perimity.guard.config;

import com.perimity.guard.security.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * guard-service (Palash) - port 8085.
 *
 * Day 7 gate: guard endpoints require the GUARD role and an open session.
 *
 * ==========================================================================
 * THIS CLASS CLOSES A REAL HOLE, NOT A THEORETICAL ONE
 * ==========================================================================
 * Until today POST /api/guard/scan was reachable with no token at all, and
 * ScanRequestDto.guardUserId arrived in the REQUEST BODY. Any caller could post
 * a scan claiming to be any guard, at any gate, and the entry log would record
 * it as fact.
 *
 * The comment in ScanService says the session lookup is "what makes the entry
 * log evidence rather than a claim". That is only true once guardUserId comes
 * from a verified token. Adding this file is step one; deleting guardUserId
 * from the DTO is step two, and both are needed. See DAY-7-README.md section 5.
 *
 * ==========================================================================
 * WHY THE ROLE RULE IS NOT ENOUGH ON ITS OWN
 * ==========================================================================
 * hasRole('GUARD') proves the caller is a guard. It does not prove they are on
 * shift. ScanService.requireOpenSession already enforces that, and it is the
 * second half of the Day 7 gate - the wording is "the GUARD role AND an open
 * session". Both halves are in place once guardUserId comes from the principal.
 *
 * ==========================================================================
 * WHO ELSE READS THE LOG
 * ==========================================================================
 * A guard sees their own shift. A Campus Admin sees their campus (screen 15).
 * Faculty need the attendance figures for an event they organise (screen 12),
 * so they are permitted on that one path and no other. A student never reads
 * the register.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource()))
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

                    // ---- The four paths that must be public in every service ----
                    .requestMatchers("/api/guard/ping").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                     "/api-docs", "/api-docs/**",
                                     "/v3/api-docs", "/v3/api-docs/**").permitAll()

                    // ---- Day 10 scanner wireframe. The PAGE is public; every
                    //      API it calls is not. It holds no data of its own - the
                    //      guard pastes a token into it - so serving it openly
                    //      leaks nothing, exactly like the Swagger page above.
                    //      Delete this line when the React shell takes over. ----
                    .requestMatchers("/scanner.html").permitAll()

                    // ---- Day 12 health (SRS 5.6). Public because Docker and the
                    //      load balancer poll it with no credentials - a health
                    //      check that needs a token marks every container
                    //      unhealthy and restarts it forever.
                    //
                    //      It reveals which dependencies are reachable, which is
                    //      the point, and no data of its own. Only `health` is
                    //      exposed - see application.properties. Do NOT widen
                    //      that to `*`: env and configprops would publish this
                    //      service's configuration to the same audience. ----
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                    // ---- Nothing calls into guard-service service-to-service yet.
                    //      The matcher is here so the pattern matches the other five
                    //      and a future internal endpoint cannot land outside it. ----
                    .requestMatchers("/api/internal/**").permitAll()

                    // ---- The gate. GUARD only, no exceptions, not even an admin -
                    //      an admin scanning would produce an entry log with no
                    //      shift behind it. ----
                    .requestMatchers(HttpMethod.POST, "/api/guard/scan").hasRole("GUARD")

                    // ---- Shift start and end are the guard's own actions ----
                    .requestMatchers(HttpMethod.POST, "/api/guard/sessions").hasRole("GUARD")
                    .requestMatchers(HttpMethod.POST, "/api/guard/sessions/*/end").hasRole("GUARD")
                    .requestMatchers(HttpMethod.GET, "/api/guard/sessions/current",
                                                     "/api/guard/sessions/history")
                            .hasRole("GUARD")

                    // ---- Who is on shift right now, across the campus: supervision ----
                    .requestMatchers(HttpMethod.GET, "/api/guard/sessions/open")
                            .hasAnyRole("CAMPUS_ADMIN", "SUPER_ADMIN")

                    // ---- Organiser attendance. Faculty run events, so they need
                    //      this one path (FR-SCAN, screen 12). ----
                    .requestMatchers(HttpMethod.GET, "/api/guard/entry-logs/events/*/attendance")
                            .hasAnyRole("FACULTY", "CAMPUS_ADMIN", "SUPER_ADMIN")

                    // ---- The register itself. Note /search and /stats are POST
                    //      because the filter is a body, not a query string - so the
                    //      matcher must name POST or these fall through. ----
                    .requestMatchers(HttpMethod.POST, "/api/guard/entry-logs/search",
                                                      "/api/guard/entry-logs/stats")
                            .hasAnyRole("GUARD", "CAMPUS_ADMIN", "SUPER_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/guard/entry-logs/**")
                            .hasAnyRole("GUARD", "CAMPUS_ADMIN", "SUPER_ADMIN")

                    .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
