package com.perimity.auth.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Takes SMTP off the request thread.
 *
 * POST /api/auth/otp/request took 5.0 seconds, all of it waiting for Gmail to
 * accept the message, while the caller sat on an open connection. A visitor
 * registering saw a spinner for five seconds and, if anything cancelled the
 * request in that window, a "could not reach the server" on a healthy system.
 *
 * qr-service reached the same conclusion for pass emails on Day 9 and moved
 * them onto a queue; its listener still carries the note about the 50-200ms
 * this used to add. Five seconds is that problem an order of magnitude worse.
 *
 * A small bounded pool rather than the default SimpleAsyncTaskExecutor, which
 * starts an unbounded number of threads - a burst of registrations would
 * otherwise open a thread per email and a socket per thread.
 *
 * CallerRunsPolicy on saturation, deliberately: if the queue is full the
 * sending thread does the work itself, which slows that one request down
 * instead of silently dropping somebody's code on the floor.
 */
@Configuration
@EnableAsync
public class MailAsyncConfig {

    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("auth-mail-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Let an in-flight code finish sending on shutdown. Losing one here
        // means a visitor waits for an email that was never going to arrive.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
