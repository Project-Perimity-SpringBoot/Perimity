package com.perimity.qr.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * DAY 10. The executor that gets email off the generation path, and the
 * scheduler that runs the reconciliation sweep.
 *
 * ==========================================================================
 * WHY EMAIL MOVED OFF THE CONSUMER THREAD
 * ==========================================================================
 * An SMTP handshake plus a send is 50-200ms, and until today it ran inline on
 * the RabbitMQ consumer thread. One approval never noticed. Six hundred bulk
 * rows is 30 to 120 seconds of a generation pipeline doing nothing but waiting
 * on a mail server - and Arham's progress bar would sit still for all of it,
 * because a job is not DONE until its listener method returns.
 *
 * ==========================================================================
 * WHY AN EXECUTOR AND NOT ANOTHER QUEUE
 * ==========================================================================
 * Day 9's README said this would move onto its own RabbitMQ queue. That was
 * the wrong call and this is the correction.
 *
 * A queue would mean a second message carrying the holder's name and email
 * address, sitting in the broker and then in the DLQ after a failure - a second
 * copy of personal data, in a second place, to solve a threading problem. The
 * durability a queue would buy is already covered: generation_jobs.email_status
 * is the durable record, and ReconciliationService finds anything left at
 * PENDING after a crash. So the queue costs PII exposure and five new classes
 * and buys something we already have.
 *
 * ==========================================================================
 * WHY THE POOL IS BOUNDED, AND WHY CallerRunsPolicy
 * ==========================================================================
 * An unbounded queue in front of an executor is a memory leak with good
 * manners: 600 rows arrive faster than SMTP drains them, the backlog grows, and
 * the failure is an OutOfMemoryError with no hint of a cause.
 *
 * CallerRunsPolicy is the important half. When the pool and its queue are both
 * full, the task runs on the thread that submitted it - the consumer thread -
 * which slows message consumption to exactly the rate the mail server can
 * sustain. That is backpressure: the system degrades to Day 9's behaviour
 * rather than falling over. AbortPolicy, the default, would instead throw and
 * lose the email.
 *
 * Java 17, so no virtual threads. On 21 this would be a virtual-thread executor
 * and the bounding would matter less - worth saying if anyone asks.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    public static final String EMAIL_EXECUTOR = "passEmailExecutor";

    @Bean(name = EMAIL_EXECUTOR)
    public Executor passEmailExecutor(
            @Value("${qr.email.pool.core-size:4}") int coreSize,
            @Value("${qr.email.pool.max-size:8}") int maxSize,
            @Value("${qr.email.pool.queue-capacity:200}") int queueCapacity) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("pass-email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        /*
         * Wait for in-flight sends on shutdown. Without this, Ctrl-C during a
         * bulk run kills threads mid-send and leaves jobs at email_status
         * PENDING with no record of whether the message actually left. Ten
         * seconds is enough for a queued send and short enough not to hang a
         * restart.
         */
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        executor.initialize();
        return executor;
    }
}
