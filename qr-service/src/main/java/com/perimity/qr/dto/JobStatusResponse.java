package com.perimity.qr.dto;

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
}
