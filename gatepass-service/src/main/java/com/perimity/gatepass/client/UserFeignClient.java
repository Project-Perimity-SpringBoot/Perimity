package com.perimity.gatepass.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** user-service, over Feign. Supplies the photo key printed on the pass PDF. */
@FeignClient(
        name = "user-service",
        contextId = "gatepassUser",
        url = "${perimity.services.user-url:}",
        configuration = FeignSupportConfig.class,
        fallbackFactory = FeignFallbacks.User.class)
public interface UserFeignClient {

    @GetMapping("/api/user/internal/profiles/{id}/summary")
    InternalServiceClient.ProfileEnvelope profile(@PathVariable("id") Long userId);
}
