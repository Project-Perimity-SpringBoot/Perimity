package com.perimity.qr.dto;

import com.perimity.qr.entity.enums.EmailStatus;
import com.perimity.qr.entity.enums.JobStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** What GET /api/qr/jobs/{jobId}/status returns. */
@Getter
@Builder
@AllArgsConstructor
public class JobStatusResponse {

    private Long jobId;
    private Long passId;
    private Long batchId;
    private JobStatus status;
    private int retryCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    /**
     * DAY 9. Whether the holder was actually told about their pass.
     *
     * Surfaced here rather than left in the database because "the pass was
     * generated" and "the visitor received it" are different facts, and only
     * one of them gets someone through a gate. A DONE job with a FAILED email
     * is a person standing outside with nothing on their phone.
     */
    private EmailStatus emailStatus;
    private String emailError;
    private LocalDateTime emailSentAt;
}
