package com.perimity.auth.config;

import com.perimity.auth.security.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * ============================================================
 *  THIS IS THE FILE THE OTHER FIVE SERVICES COPY. Post it today.
 * ============================================================
 *
 * Adding spring-boot-starter-security secures EVERY endpoint by default,
 * including /ping and Swagger. This class is what re-opens them.
 *
 * Leaving /ping locked is a real production failure, not a nuisance: Docker
 * polls it as a healthcheck, gets 401, marks the container unhealthy and
 * restarts it forever.
 *
 * FOUR PATHS MUST BE PUBLIC IN EVERY SERVICE. Only the service name changes:
 *
 *     /api/<service>/ping
 *     /swagger-ui.html
 *     /swagger-ui/**
 *     /v3/api-docs/**        and /api-docs/** where that path is configured
 *
 * Note the api-docs split: four services set springdoc.api-docs.path=/api-docs
 * while this one uses /v3/api-docs. Both are permitted below, but the team
 * should agree one value.
 *
 * auth-service additionally opens login, OTP, visitor registration and the two
 * password-reset endpoints - you cannot obtain a token without reaching them.
 * They are listed ONE BY ONE rather than as /api/auth/**, so a new endpoint
 * added under that path does not become public by accident.
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
                    .requestMatchers("/api/auth/ping").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                     "/v3/api-docs", "/v3/api-docs/**",
                                     "/api-docs", "/api-docs/**").permitAll()

                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/otp/request").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/otp/verify").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/visitors/register").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/password/reset-request").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/password/reset-confirm").permitAll()

                    .anyRequest().authenticated())
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

    /** bcrypt. Strength 10 is the Spring default and is right here. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
