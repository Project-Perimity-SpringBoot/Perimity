package com.perimity.qr.dto;

import com.perimity.qr.entity.enums.EmailStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * DAY 10. One pass whose holder was never told about it.
 *
 * Deliberately carries NO email address. qr-service does not store holder
 * addresses and this endpoint is not a reason to start - personal data belongs
 * in the service that owns identity, and an endpoint that returned a list of
 * everyone's email would be a far more attractive target than one that returns
 * a list of pass ids.
 *
 * So this names the passes and gatepass-service, which already holds the
 * addresses, drives the resends. The trade is one extra lookup on their side
 * against a second copy of everyone's email address on ours.
 */
@Getter
@Builder
@AllArgsConstructor
public class UndeliveredEmailResponse {

    private Long jobId;
    private Long passId;
    private Long batchId;
    private EmailStatus emailStatus;

    /** Null when the email was never attempted rather than attempted and refused. */
    private String emailError;

    private LocalDateTime jobCompletedAt;
}
