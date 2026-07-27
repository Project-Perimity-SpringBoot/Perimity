package com.perimity.qr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * What GET /api/qr/jobs/batch/{batchId}/progress returns.
 *
 * Arham's Bulk Progress screen (10) polls this. Team Guide says it must exist
 * by Day 16 - the counts below are already answerable from repository methods
 * that exist today, so it does not have to wait for Day 8.
 */
@Getter
@Builder
@AllArgsConstructor
public class BatchProgressResponse {

    private Long batchId;
    private long total;
    private long queued;
    private long processing;
    private long done;
    private long failed;

    /** Rounded down, 0-100. What the progress bar binds to. */
    private int percentComplete;

    /** True when nothing is left in QUEUED or PROCESSING. */
    private boolean finished;
}
