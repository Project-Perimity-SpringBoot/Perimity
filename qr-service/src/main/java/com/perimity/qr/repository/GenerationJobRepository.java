package com.perimity.qr.repository;

import com.perimity.qr.entity.GenerationJob;
import com.perimity.qr.entity.enums.JobStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByPassId(Long passId);

    List<GenerationJob> findByStatus(JobStatus status);

    /** Bulk Progress screen: how many of this batch are done, failed, still queued. */
    long countByBatchIdAndStatus(Long batchId, JobStatus status);

    long countByBatchId(Long batchId);

    List<GenerationJob> findByBatchIdAndStatus(Long batchId, JobStatus status);

    /** Retry-failed-rows only: the failed jobs of one batch, nothing else. */
    @Query("""
            SELECT j FROM GenerationJob j
            WHERE j.batchId = :batchId
              AND j.status = :status
              AND j.retryCount < :maxRetries
            """)
    List<GenerationJob> findRetryableJobs(@Param("batchId") Long batchId,
                                          @Param("status") JobStatus status,
                                          @Param("maxRetries") int maxRetries);

    default List<GenerationJob> findRetryableJobs(Long batchId, int maxRetries) {
        return findRetryableJobs(batchId, JobStatus.FAILED, maxRetries);
    }

    /** Jobs stuck in PROCESSING because the service died mid-job. */
    List<GenerationJob> findByStatusAndRetryCountLessThan(JobStatus status, int maxRetries);
}
