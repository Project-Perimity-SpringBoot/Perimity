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
 *
 * TWO SCHEMES, because this service has two kinds of caller and they do not
 * authenticate the same way:
 *
 *   bearerAuth      a person, holding a JWT from POST /api/auth/login
 *   internalApiKey  one of our own six services, holding the shared
 *                   INTERNAL_API_KEY, on every /api/internal/** path
 *
 * The second one was missing, which is why /api/internal/auth/** answered 401
 * from Swagger even with a valid token pasted in: no token of any kind is the
 * right credential there, and Swagger had no field for the one that is.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";
    public static final String INTERNAL_SCHEME = "internalApiKey";

    @Bean
    public OpenAPI authOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Perimity - Auth Service")
                        .version("v1")
                        .description("""
                                Identity, credentials, one-time codes, blocklist and audit trail.

                                Sign in with POST /api/auth/login, copy the token from the
                                response, then click Authorize and paste it. Every other
                                service accepts the same token.

                                The Internal endpoints are different: they are called by other
                                services, never by a browser, and take no JWT. Click Authorize
                                and fill in internalApiKey with the INTERNAL_API_KEY value from
                                the repo-root .env instead.
                                """))
                // The default for a human-facing endpoint. The two Internal
                // controllers override this with their own @SecurityRequirement.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSecuritySchemes(INTERNAL_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        // Must match InternalApiKeyFilter.HEADER exactly.
                                        .name("X-Internal-Api-Key")
                                        .description("""
                                                The shared INTERNAL_API_KEY from the repo-root .env.
                                                Service-to-service only - a browser should never
                                                have this value.
                                                """)));
    }
}
