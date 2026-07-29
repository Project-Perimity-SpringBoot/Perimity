package com.perimity.auth.config;

import com.perimity.auth.security.JwtAuthenticationFilter;
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
import java.util.List;

/**
 * Security for auth-service.
 *
 * IMPORTANT for the team: spring-boot-starter-security is on the classpath, so
 * without this class Spring Boot secures EVERY endpoint behind generated basic
 * auth - including /ping and Swagger. That is why /ping and the springdoc paths
 * are explicitly permitted below.
 *
 * Leaving /ping out is a real production failure, not a nuisance: Docker polls
 * it as a healthcheck, gets 401, marks the container unhealthy and restarts it
 * forever.
 *
 * Copy the permit list into the other five services on Day 7. The endpoints
 * differ, but /ping and the three springdoc paths must be public everywhere.
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
            .authorizeHttpRequests(auth -> auth
                    // health and docs - public in every service, always
                    .requestMatchers("/api/auth/ping").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                     "/v3/api-docs", "/v3/api-docs/**").permitAll()

                    // the endpoints you must be able to reach without a token,
                    // because reaching them is how you get one
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

    /** bcrypt. Strength 10 is the Spring default and is fine here. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
