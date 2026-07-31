package com.perimity.gatepass.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** auth-service, over Feign. Supplies the holder email a pass is sent to. */
@FeignClient(
        name = "auth-service",
        contextId = "gatepassAuth",
        url = "${perimity.services.auth-url:}",
        configuration = FeignSupportConfig.class,
        fallbackFactory = FeignFallbacks.Auth.class)
public interface AuthFeignClient {

    // NOTE the path shape. auth-service uses /api/internal/auth/** while every
    // other service uses /api/<service>/internal/**. Getting this wrong produces
    // a 404 that the fallback turns into "no email found", so the pass is issued
    // and simply never emailed - a silent failure worth knowing about.
    @GetMapping("/api/internal/auth/users/{id}/email")
    InternalServiceClient.EmailEnvelope email(@PathVariable("id") Long userId);
}
