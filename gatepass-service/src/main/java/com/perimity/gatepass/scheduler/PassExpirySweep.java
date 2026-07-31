package com.perimity.gatepass.scheduler;

import com.perimity.gatepass.service.GatePassService;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moves finished passes to EXPIRED once a day.
 *
 * Why a sweep rather than computing expiry at scan time: the gate must answer
 * in about a second, and a stored status is a single indexed read. It also
 * means "expired" is a fact in the database that reports and dashboards can
 * count, not something each caller recalculates and occasionally disagrees on.
 *
 * Runs at 00:05 in Asia/Kolkata - just after midnight, so a pass valid "until
 * the 12th" still works for the whole of the 12th. The application sets that
 * timezone as the first line of main().
 *
 * NOTE for deployment: with more than one instance running, every instance runs
 * this. It is harmless here because the work is idempotent - the second run
 * finds nothing left to expire - but if a notification is ever attached to
 * expiry, this needs a lock (ShedLock) first.
 */
@Component
public class PassExpirySweep {

    private static final Logger log = LoggerFactory.getLogger(PassExpirySweep.class);

    private final GatePassService gatePassService;

    public PassExpirySweep(GatePassService gatePassService) {
        this.gatePassService = gatePassService;
    }

    @Scheduled(cron = "${perimity.pass.expiry-cron:0 5 0 * * *}", zone = "Asia/Kolkata")
    public void sweep() {
        LocalDate today = LocalDate.now();
        List<Long> due = gatePassService.findPassesDueToExpire(today);

        if (due.isEmpty()) {
            log.debug("Pass expiry sweep: nothing due as of {}", today);
            return;
        }

        int expired = 0;
        int failed = 0;
        for (Long id : due) {
            try {
                gatePassService.expireOne(id);
                expired++;
            } catch (RuntimeException ex) {
                // One unhealthy row must never stop the sweep. Log it loudly and
                // keep going - a stale pass left green is worse than a noisy log.
                failed++;
                log.error("Pass expiry sweep: could not expire pass {} - {}", id, ex.getMessage());
            }
        }

        if (failed > 0) {
            log.warn("Pass expiry sweep: {} expired, {} FAILED as of {}", expired, failed, today);
        } else {
            log.info("Pass expiry sweep: {} pass(es) expired as of {}", expired, today);
        }
    }
}
