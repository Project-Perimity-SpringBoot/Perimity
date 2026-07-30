package com.perimity.auth.config;

import com.perimity.auth.security.InternalApiKeyFilter;
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
 * FOUR PATHS MUST BE PUBLIC IN EVERY SERVICE. Only the service name changes:
 *
 *     /api/<service>/ping
 *     /swagger-ui.html
 *     /swagger-ui/**
 *     /v3/api-docs/**        and /api-docs/** where that path is configured
 *
 * PLUS a fifth, added Day 8: /api/internal/** is permitAll here too, because a
 * service-to-service call never carries a JWT. It is not actually open -
 * InternalApiKeyFilter, registered below, is what locks it down.
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
    private final InternalApiKeyFilter internalApiKeyFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          InternalApiKeyFilter internalApiKeyFilter) {
        this.jwtFilter = jwtFilter;
        this.internalApiKeyFilter = internalApiKeyFilter;
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

                    // Day 8 - internal, service-to-service only. Not a human
                    // caller, so not JWT. Without this line FilterSecurityInterceptor
                    // demands a Bearer token no internal call will ever present,
                    // and every one dies 401 before InternalApiKeyFilter even runs.
                    .requestMatchers("/api/internal/**").permitAll()

                    .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(internalApiKeyFilter, JwtAuthenticationFilter.class);

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
