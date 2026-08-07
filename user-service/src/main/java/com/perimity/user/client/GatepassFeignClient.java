package com.perimity.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative call into gatepass-service.
 *
 * SRS v1.1: when a sensitive profile field changes - government id, photo, roll
 * number - every active pass that person holds must be paused for re-approval.
 * A changed photo on a still-active pass is exactly the hole that rule closes.
 *
 * name = "gatepass-service" is resolved through Eureka. The url attribute is
 * the fallback for when eureka.client.enabled=false.
 */
@FeignClient(
        name = "gatepass-service",
        contextId = "userPassPause",
        url = "${perimity.services.gatepass-url:}",
        configuration = FeignSupportConfig.class)
public interface GatepassFeignClient {

    @PostMapping("/api/gatepass/internal/passes/holder/{id}/pause")
    PauseEnvelope pauseHolder(@PathVariable("id") Long holderUserId,
                              @RequestBody PauseRequest request);

    /**
     * The release, called when faculty approve a profile. Same body as pause -
     * what happened, and who is accountable for it.
     */
    @PostMapping("/api/gatepass/internal/passes/holder/{id}/resume")
    PauseEnvelope resumeHolder(@PathVariable("id") Long holderUserId,
                               @RequestBody PauseRequest request);

    @PostMapping("/api/gatepass/internal/passes/issue")
    IssuePassEnvelope issuePass(@RequestBody IssuePassRequest request);

    record PauseRequest(String reason, Long changedBy) { }

    record PauseEnvelope(boolean success, String message, Object data) { }

    record IssuePassRequest(
            Long holderUserId,
            String holderName,
            Long campusId,
            Long visitorRequestId,
            String passType,
            Long eventId,
            java.time.LocalDate validFrom,
            java.time.LocalDate validTo
    ) { }

    record IssuePassEnvelope(boolean success, String message, Object data) { }
}
