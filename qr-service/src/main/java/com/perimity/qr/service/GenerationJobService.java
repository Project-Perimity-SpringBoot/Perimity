package com.perimity.qr.service;

import com.perimity.qr.dto.BatchProgressResponse;
import com.perimity.qr.dto.JobStatusResponse;
import com.perimity.qr.entity.GenerationJob;
import com.perimity.qr.entity.enums.JobStatus;
import com.perimity.qr.repository.GenerationJobRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GenerationJobService {

    private final GenerationJobRepository generationJobRepository;

    public GenerationJobService(GenerationJobRepository generationJobRepository) {
        this.generationJobRepository = generationJobRepository;
    }

    /** What GET /api/qr/jobs/{jobId}/status answers. */
    @Transactional(readOnly = true)
    public JobStatusResponse getStatus(Long jobId) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No generation job with id " + jobId));

        return JobStatusResponse.builder()
                .jobId(job.getId())
                .passId(job.getPassId())
                .batchId(job.getBatchId())
                .status(job.getStatus())
                .retryCount(job.getRetryCount())
                .errorMessage(job.getErrorMessage())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }

    /**
     * What GET /api/qr/jobs/batch/{batchId}/progress answers - Arham's Bulk
     * Progress screen polls this.
     *
     * A batch with no rows is a 404, not a 0% bar. "Batch 999 does not exist"
     * and "batch 999 has not started" are different facts, and a progress bar
     * frozen at 0 cannot tell the user which one they are looking at.
     */
    @Transactional(readOnly = true)
    public BatchProgressResponse getBatchProgress(Long batchId) {
        long total = generationJobRepository.countByBatchId(batchId);
        if (total == 0) {
            throw new EntityNotFoundException("No generation jobs for batchId " + batchId);
        }

        long queued = generationJobRepository.countByBatchIdAndStatus(batchId, JobStatus.QUEUED);
        long processing = generationJobRepository.countByBatchIdAndStatus(batchId, JobStatus.PROCESSING);
        long done = generationJobRepository.countByBatchIdAndStatus(batchId, JobStatus.DONE);
        long failed = generationJobRepository.countByBatchIdAndStatus(batchId, JobStatus.FAILED);

        // Terminal means settled, not successful. A failed row is finished
        // being worked on, so it counts toward the bar - otherwise a batch
        // where every row failed would sit at 0% forever looking like a hang.
        long settled = done + failed;
        int percent = (int) ((settled * 100) / total);

        return BatchProgressResponse.builder()
                .batchId(batchId)
                .total(total)
                .queued(queued)
                .processing(processing)
                .done(done)
                .failed(failed)
                .percentComplete(percent)
                .finished(settled == total)
                .build();
    }
}
