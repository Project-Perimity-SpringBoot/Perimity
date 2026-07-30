package com.perimity.campus.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds the Authorize button to Swagger.
 *
 * Without it every endpoint returns 401 the moment security is added and it
 * looks like the service broke. It did not - there was just no way to attach a
 * token.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI campusOpenApi() {
        final String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Perimity - Campus Service")
                        .version("v1")
                        .description("""
                                Institutions, gates and per-campus policy.

                                Click Authorize and paste a JWT from auth-service /api/auth/login.
                                Endpoints under /internal need the X-Internal-Api-Key header
                                instead - Swagger cannot call those.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme().name(scheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")));
    }
}
