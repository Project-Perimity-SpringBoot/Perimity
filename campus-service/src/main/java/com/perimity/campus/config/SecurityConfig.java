package com.perimity.campus.config;

import com.perimity.campus.security.InternalApiKeyFilter;
import com.perimity.campus.security.JwtAuthenticationFilter;
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
 * Security for campus-service.
 *
 * Same shape as the other five. The permit list is what differs, and /ping plus
 * the springdoc paths must be public in every one of them - Docker polls /ping
 * as a healthcheck, and a 401 there makes it restart the container forever.
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
                    // 401 means bring a token. 403 means your token is not
                    // enough. Without this Spring answers 403 to an anonymous
                    // caller, which says the wrong thing entirely.
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
                    .requestMatchers("/api/campus/ping").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                     "/v3/api-docs", "/v3/api-docs/**",
                                     "/api-docs", "/api-docs/**").permitAll()

                    // Development file serving. Only reachable when storage is
                    // local, and it must NOT survive to production - S3 serves
                    // its own presigned URLs.
                    .requestMatchers("/api/campus/storage/local/**").permitAll()

                    // Authenticated by the API key filter, which runs first.
                    .requestMatchers("/api/campus/internal/**").hasRole("INTERNAL")

                    .anyRequest().authenticated())
            .addFilterBefore(internalKeyFilter, UsernamePasswordAuthenticationFilter.class)
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
