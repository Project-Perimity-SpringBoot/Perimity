package com.perimity.gatepass.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Puts an Authorize button in Swagger UI.
 *
 * Without this, every endpoint returns 401 from Swagger the moment security is
 * added, and the obvious conclusion is "Day 7 broke everything". It did not -
 * there was simply no way to attach a token.
 *
 * Paste the JWT from auth-service's /login into Authorize, once. Swagger then
 * sends it on every request.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gatepassOpenApi() {
        final String scheme = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Perimity - Gate Pass Service")
                        .version("v1")
                        .description("""
                                Visitor requests, events, gate passes and the bulk engine.

                                Click Authorize and paste the JWT from auth-service /api/auth/login.
                                Endpoints under /internal are service-to-service and need the
                                X-Internal-Api-Key header instead - Swagger cannot call those.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .name(scheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
