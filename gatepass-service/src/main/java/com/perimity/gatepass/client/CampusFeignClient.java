package com.perimity.gatepass.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * campus-service, over Feign.
 *
 * contextId is required because this service declares more than one
 * @FeignClient. Without it Spring cannot tell two client beans apart and
 * startup fails with a duplicate bean name.
 */
@FeignClient(
        name = "campus-service",
        contextId = "gatepassCampus",
        url = "${perimity.services.campus-url:}",
        configuration = FeignSupportConfig.class,
        fallbackFactory = FeignFallbacks.Campus.class)
public interface CampusFeignClient {

    @GetMapping("/api/campus/internal/campuses/{id}")
    InternalServiceClient.CampusEnvelope campus(@PathVariable("id") Long campusId);
}
