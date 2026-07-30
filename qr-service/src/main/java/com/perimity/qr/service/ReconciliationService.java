package com.perimity.qr.service;

import com.perimity.qr.entity.GenerationJob;
import com.perimity.qr.entity.enums.EmailStatus;
import com.perimity.qr.entity.enums.JobStatus;
import com.perimity.qr.messaging.QrResultPublisher;
import com.perimity.qr.repository.GenerationJobRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * DAY 10. Finds work that started and never finished, and says so.
 *
 * ==========================================================================
 * THE GAP THIS CLOSES
 * ==========================================================================
 * Every failure path built on Days 8 to 11 assumes the consumer lives long
 * enough to record what went wrong. Kill the service between claim() and
 * markDone and none of them run: the job sits at PROCESSING, gatepass never
 * hears a result, the pass stays PENDING, and there is no error anywhere
 * because nothing failed - the process simply stopped existing.
 *
 * One such row is a curiosity. Across a 600-row batch it is Arham's progress
 * bar frozen at 97% with no failure count, which is precisely the hang the
 * Day 17 gate is written to catch. It is also the state qrdb has been sitting
 * in all week: a QUEUED job for a pass nobody is coming back for.
 *
 * ==========================================================================
 * WHAT IT DELIBERATELY DOES NOT DO
 * ==========================================================================
 * It does not retry generation. A job stuck at PROCESSING may have completed
 * its work and died before committing, and re-running it would mint a second
 * token for a pass whose QR might already be in someone's inbox. Marking it
 * FAILED and telling gatepass is honest; guessing is not. Tushar's /republish
 * endpoint is the deliberate, human-initiated retry.
 *
 * It does not resend emails either, for a reason worth stating plainly:
 * qr-service does not store holder email addresses, and it should not start
 * storing them to make a sweep convenient. Personal data belongs in the service
 * that owns identity. So failed emails are counted and exposed, and the resend
 * is driven by whoever holds the address.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final GenerationJobRepository generationJobRepository;
    private final GenerationJobService generationJobService;
    private final QrResultPublisher resultPublisher;
    private final int stuckAfterMinutes;

    public ReconciliationService(GenerationJobRepository generationJobRepository,
                                 GenerationJobService generationJobService,
                                 QrResultPublisher resultPublisher,
                                 @Value("${qr.reconcile.stuck-after-minutes:10}")
                                 int stuckAfterMinutes) {
        this.generationJobRepository = generationJobRepository;
        this.generationJobService = generationJobService;
        this.resultPublisher = resultPublisher;
        this.stuckAfterMinutes = stuckAfterMinutes;
    }

    /**
     * Settles jobs abandoned mid-flight.
     *
     * fixedDelayString, not fixedRate: fixedRate schedules from the START of the
     * previous run, so a sweep that takes longer than its interval gets a second
     * copy launched on top of it. fixedDelay waits for the previous run to
     * finish first, which is the only sane choice for something that writes.
     *
     * The ten-minute threshold is generous on purpose. Spring AMQP's retry
     * backoff can legitimately hold a job in PROCESSING for around fifteen
     * seconds, and a bulk batch under load stretches that further. Sweeping too
     * eagerly would mark live work as failed, which is worse than noticing a
     * genuine failure ten minutes late.
     */
    @Scheduled(fixedDelayString = "${qr.reconcile.interval-ms:120000}",
               initialDelayString = "${qr.reconcile.initial-delay-ms:60000}")
    public void settleAbandonedJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(stuckAfterMinutes);

        List<GenerationJob> stuck =
                generationJobRepository.findByStatusAndStartedAtBefore(JobStatus.PROCESSING, cutoff);

        if (stuck.isEmpty()) {
            return;
        }

        log.warn("Reconciliation found {} job(s) stuck in PROCESSING for over {} minutes",
                stuck.size(), stuckAfterMinutes);

        for (GenerationJob job : stuck) {
            String reason = "Abandoned in PROCESSING for over " + stuckAfterMinutes
                    + " minutes - the consumer stopped before settling this job";

            generationJobService.markFailed(job.getId(), reason);

            /*
             * Tell gatepass too, not just the local row.
             *
             * Without this the pass sits at PENDING forever: gatepass waits to
             * be told, and the message that would have told it died with the
             * consumer. A failure result is what lets a human see the pass and
             * republish it.
             */
            resultPublisher.publishFailure(
                    job.getJobRef(), job.getPassId(), job.getBatchId(), reason, job.getRetryCount());

            log.warn("Settled abandoned job {} (pass {}, batch {}) as FAILED",
                    job.getId(), job.getPassId(), job.getBatchId());
        }
    }

    /**
     * Reports emails that never went out. Reports only - see the class comment
     * for why this cannot resend them itself.
     *
     * PENDING as well as FAILED: PENDING means the executor never got to it,
     * which is what a crash mid-batch leaves behind, and it is just as invisible
     * to the holder as an outright failure.
     */
    @Scheduled(fixedDelayString = "${qr.reconcile.email-interval-ms:300000}",
               initialDelayString = "${qr.reconcile.initial-delay-ms:60000}")
    public void reportUndeliveredEmails() {
        List<GenerationJob> failed =
                generationJobRepository.findByEmailStatusOrderByIdAsc(EmailStatus.FAILED);

        List<GenerationJob> pending =
                generationJobRepository.findByEmailStatusOrderByIdAsc(EmailStatus.PENDING).stream()
                        .filter(job -> job.getStatus() == JobStatus.DONE)
                        .toList();

        if (failed.isEmpty() && pending.isEmpty()) {
            return;
        }

        log.warn("{} pass email(s) failed and {} still pending on completed jobs. "
                        + "Passes are fine; the holders have not been told. "
                        + "GET /api/qr/internal/emails/undelivered lists them.",
                failed.size(), pending.size());
    }
}
