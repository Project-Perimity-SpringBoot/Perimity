package com.perimity.gatepass.config;

import com.perimity.gatepass.security.InternalApiKeyFilter;
import com.perimity.gatepass.security.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security for gatepass-service.
 *
 * READ THIS BEFORE COPYING IT INTO YOUR OWN SERVICE.
 *
 * Adding spring-boot-starter-security secures EVERY endpoint by default,
 * including /ping and Swagger. This class is what re-opens them. Leaving /ping
 * locked is a real production failure, not a nuisance: Docker polls it as a
 * healthcheck, gets 401, marks the container unhealthy and restarts it forever.
 *
 * These four must be public in EVERY service. Only the service name changes:
 *
 *     /api/<service>/ping
 *     /swagger-ui.html
 *     /swagger-ui/**
 *     /v3/api-docs/**      (and /api-docs/** where that path is configured)
 *
 * Two filters run, on different paths:
 *   JwtAuthenticationFilter  - user tokens, everywhere
 *   InternalApiKeyFilter     - shared key, only on /internal/**
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final InternalApiKeyFilter internalKeyFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          InternalApiKeyFilter internalKeyFilter) {
        this.jwtFilter = jwtFilter;
        this.internalKeyFilter = internalKeyFilter;
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
                    // Without this Spring answers 403 for an anonymous caller,
                    // which tells the client "you are logged in but not allowed"
                    // when the truth is "you are not logged in". 401 means bring
                    // a token; 403 means your token is not enough.
                    .authenticationEntryPoint((req, res, ex) -> {
                        res.setStatus(401);
                        res.setContentType("application/json");
                        res.getWriter().write(
                                "{\"success\":false,\"message\":\"Authentication required\","
                                        + "\"data\":null,\"errors\":[]}");
                    })
                    .accessDeniedHandler((req, res, ex) -> {
                        res.setStatus(403);
                        res.setContentType("application/json");
                        res.getWriter().write(
                                "{\"success\":false,\"message\":\"Your role is not permitted "
                                        + "to perform this action\",\"data\":null,\"errors\":[]}");
                    }))
            .authorizeHttpRequests(auth -> auth
                    // health and docs
                    .requestMatchers("/api/gatepass/ping").permitAll()

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
                                     "/v3/api-docs", "/v3/api-docs/**",
                                     "/api-docs", "/api-docs/**").permitAll()

                    // Development file serving - Day 10. This is how the
                    // errors.csv download link works before AWS exists.
                    //
                    // permitAll on a route that serves files off disk deserves
                    // a second look, so the reasoning is written down:
                    //
                    //  1. The bean does not exist in production.
                    //     LocalStorageController is @ConditionalOnProperty on
                    //     storage.type=local, so with type=s3 the route is
                    //     never registered and this line permits nothing.
                    //  2. Path traversal is blocked at the filesystem layer -
                    //     LocalFileStorageService.resolve() normalises the key
                    //     and throws if it escapes the storage root.
                    //  3. It IS still a gap: anyone who guesses a key can read
                    //     that object. Keys carry a UUID so guessing is
                    //     impractical, but impractical is not authorised.
                    //
                    // Same trade-off Arham accepted in campus-service, gated
                    // on the same property. Agreed as a team decision.
                    .requestMatchers("/api/gatepass/storage/local/**").permitAll()

                    // service-to-service. NOT permitAll - the API key filter
                    // authenticates these, and it runs before this check.
                    .requestMatchers("/api/gatepass/internal/**").hasRole("INTERNAL")

                    .anyRequest().authenticated())
            .addFilterBefore(internalKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Tighten to the real frontend origin before deployment. */
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
