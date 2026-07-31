package com.perimity.qr.repository;

import com.perimity.qr.entity.GenerationJob;
import com.perimity.qr.entity.enums.EmailStatus;
import com.perimity.qr.entity.enums.JobStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    /**
     * DEPRECATED for new code as of Day 8. A re-issued pass has more than one
     * job row, and this returns an Optional, so it throws
     * IncorrectResultSizeDataAccessException as soon as a second row exists.
     * Kept because Days 1-6 already reference it. Use
     * findFirstByPassIdOrderByIdDesc instead.
     */
    Optional<GenerationJob> findByPassId(Long passId);

    /**
     * The newest job for a pass. Safe when a pass has been re-issued.
     * Ordered by id rather than createdAt: ids are a monotonic IDENTITY
     * sequence, while two rows created inside the same millisecond share a
     * createdAt and their order becomes undefined - which under bulk load on
     * Day 10 is common, not theoretical.
     */
    Optional<GenerationJob> findFirstByPassIdOrderByIdDesc(Long passId);

    /**
     * Day 8 idempotency lookup: has this exact job from gatepass-service been
     * seen before? jobRef holds Tushar's QrGenerationJob.jobId.
     */
    Optional<GenerationJob> findByJobRef(String jobRef);

    List<GenerationJob> findByStatus(JobStatus status);

    /**
     * DAY 9. Jobs whose pass email did not go out.
     *
     * Backs the bulk resend on Day 10, where one unreachable mail server during
     * a 600-row upload leaves a batch of visitors with passes they were never
     * told about. Ordered so the oldest failure is retried first - the person
     * who has been waiting longest gets their pass first.
     */
    List<GenerationJob> findByEmailStatusOrderByIdAsc(EmailStatus emailStatus);

    /** DAY 9. Per-batch email counts for the Bulk Progress screen. */
    long countByBatchIdAndEmailStatus(Long batchId, EmailStatus emailStatus);

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

    /**
     * DAY 10. Jobs that started and never settled.
     *
     * The gap nothing covered until today: a job whose consumer died between
     * claim() and markDone stays PROCESSING forever. One row is a curiosity;
     * across a 600-row batch it is a progress bar frozen at 97% with no failure
     * count, because a PROCESSING job is neither done nor failed.
     */
    List<GenerationJob> findByStatusAndStartedAtBefore(JobStatus status, LocalDateTime before);

    /** Jobs stuck in PROCESSING because the service died mid-job. */
    List<GenerationJob> findByStatusAndRetryCountLessThan(JobStatus status, int maxRetries);
}
