package com.perimity.qr.email;

import com.perimity.qr.config.AsyncConfig;
import com.perimity.qr.messaging.contract.QrGenerationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * DAY 10. Hands a pass email to the executor and returns immediately.
 *
 * A separate class from PassEmailService rather than an @Async annotation on
 * one of its methods, and that is not stylistic. Spring's @Async works through
 * a proxy, so a call from inside PassEmailService to its own @Async method runs
 * synchronously - the proxy is bypassed entirely. It is one of the quietest
 * bugs in Spring: everything compiles, the annotation is right there in the
 * source, and the code is simply still blocking.
 *
 * Putting the annotation on a different bean makes the proxy unavoidable.
 */
@Component
public class PassEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PassEmailDispatcher.class);

    private final PassEmailService passEmailService;

    public PassEmailDispatcher(PassEmailService passEmailService) {
        this.passEmailService = passEmailService;
    }

    /**
     * Returns as soon as the task is queued. Never throws into the caller.
     *
     * void rather than CompletableFuture on purpose: nothing waits on the
     * result. The generation job is already DONE and gatepass has already been
     * told - the outcome of the email is recorded on the job row, which is where
     * anyone looking for it will look.
     *
     * PassEmailService swallows and records its own failures, so an exception
     * reaching here would mean a bug in that contract rather than a mail
     * problem. Logged, never rethrown: an uncaught exception on an executor
     * thread goes nowhere useful.
     */
    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void dispatch(Long jobId, QrGenerationJob message, String pdfKey) {
        try {
            passEmailService.sendPassEmail(jobId, message, pdfKey);
        } catch (RuntimeException ex) {
            log.error("Pass email dispatch failed unexpectedly for job {} (pass {}). "
                            + "PassEmailService should have handled this itself.",
                    jobId, message.passId(), ex);
        }
    }
}
