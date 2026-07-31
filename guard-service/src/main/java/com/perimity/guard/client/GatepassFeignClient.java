package com.perimity.guard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Declarative calls into gatepass-service.
 *
 * name = "gatepass-service" is the value gatepass registers under in Eureka -
 * its spring.application.name. No URL anywhere: the registry resolves it, and
 * if the service moves or scales, this code does not change.
 *
 * The url attribute below is the escape hatch. When eureka.client.enabled=false
 * Feign has no registry to ask, so it falls back to the plain property that was
 * already in use before any of this existed.
 */
@FeignClient(
        name = "gatepass-service",
        url = "${perimity.services.gatepass-url:}",
        configuration = FeignSupportConfig.class,
        dismiss404 = true)
public interface GatepassFeignClient {

    @GetMapping("/api/gatepass/internal/passes/holder/{id}/running-event")
    RunningEventEnvelope runningEvent(@PathVariable("id") Long holderUserId);

    record RunningEventEnvelope(boolean success, String message, RunningEventView data) { }

    record RunningEventView(Long eventId, String eventName) { }
}
