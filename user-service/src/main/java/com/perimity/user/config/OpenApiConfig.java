package com.perimity.user.config;

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
 * Without it every secured endpoint returns 401 from Swagger the moment Day 7
 * lands, and the obvious conclusion is "security broke everything". It did not -
 * there was simply no way to attach a token.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userOpenApi() {
        final String scheme = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Perimity - User Service")
                        .version("v1")
                        .description("""
                                Student and faculty profiles, per-campus departments, and documents.

                                This service issues no tokens. Sign in with POST /api/auth/login on
                                auth-service (port 8081), copy the token, then click Authorize here.

                                Campus scope comes from the token, never from a request parameter.
                                A Super Admin has no campus of their own and must name one with
                                ?campusId= on list endpoints.
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
