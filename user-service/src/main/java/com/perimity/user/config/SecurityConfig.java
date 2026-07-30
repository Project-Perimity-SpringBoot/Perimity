package com.perimity.user.config;

import com.perimity.user.security.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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
 * Day 7 - user-service. Adapted from the shared config auth-service publishes.
 *
 * Adding spring-boot-starter-security secures EVERY endpoint by default,
 * including /ping and Swagger. This class is what re-opens the four that must
 * stay public in every service:
 *
 *     /api/user/ping
 *     /swagger-ui.html
 *     /swagger-ui/**
 *     /api-docs/**            (this service's springdoc.api-docs.path)
 *
 * Leaving /ping locked is a real production failure, not a nuisance: Docker
 * polls it as a healthcheck, gets 401, marks the container unhealthy and
 * restarts it forever.
 *
 * NOTE ON /v3/api-docs: auth-service serves its docs there while this service
 * and three others use /api-docs. Both are permitted below so Swagger works
 * either way, but the team should still settle on one value.
 *
 * Unlike auth-service there is NO permitAll for a login route, because there is
 * nothing to log in to here. Every business endpoint needs a token issued by
 * auth-service.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          @Value("${perimity.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtFilter = jwtFilter;
        this.allowedOrigins = allowedOrigins;
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
                    // Without these, Spring answers 403 to a caller with NO
                    // token, which says "you are logged in but not allowed"
                    // when the truth is "you are not logged in".
                    // 401 means bring a token. 403 means your token is not enough.
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
                    .requestMatchers("/api/user/ping").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                     "/api-docs", "/api-docs/**",
                                     "/v3/api-docs", "/v3/api-docs/**").permitAll()
                    .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Origins come from configuration, not from a literal list, so the AWS
     * deployment on Day 22 is a .env change rather than a code change. With
     * allowCredentials(true) a wildcard is rejected by the browser anyway.
     */
    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(allowedOrigins);
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
