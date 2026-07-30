package com.perimity.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds the Authorize button to Swagger UI.
 *
 * Without it every secured endpoint returns 401 from Swagger the moment
 * security is added, and the obvious conclusion is "Day 7 broke everything".
 * It did not - there was simply no way to attach a token.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authOpenApi() {
        final String scheme = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Perimity - Auth Service")
                        .version("v1")
                        .description("""
                                Identity, credentials, one-time codes, blocklist and audit trail.

                                Sign in with POST /api/auth/login, copy the token from the
                                response, then click Authorize and paste it. Every other
                                service accepts the same token.
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
