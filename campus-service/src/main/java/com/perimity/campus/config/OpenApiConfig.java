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
        final String internal = "internalApiKey";
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
                // The default for a human-facing endpoint. CampusInternalController
                // overrides it with its own @SecurityRequirement.
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components()
                        .addSecuritySchemes(scheme,
                                new SecurityScheme().name(scheme)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer").bearerFormat("JWT"))
                        // Without this Swagger has no field for the internal key,
                        // sends an Authorization header the internal endpoints do
                        // not want, and every one of them answers 401 - which
                        // reads as a broken endpoint rather than a missing input.
                        .addSecuritySchemes(internal,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        // Must match InternalApiKeyFilter.HEADER exactly.
                                        .name("X-Internal-Api-Key")
                                        .description("The shared INTERNAL_API_KEY from the "
                                                + "repo-root .env. Service-to-service only.")));
    }
}
