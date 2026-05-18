package com.college.gatepass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures the thread pool that handles asynchronous tasks
 * (email sending, event listeners tagged with {@code @Async("appTaskExecutor")}).
 *
 * <p>Without this config, Spring would use a single-threaded executor by default,
 * which would make async calls run sequentially instead of in parallel.
 *
 * <p>Thread pool settings:
 * <ul>
 *   <li>Core size 4 — always keep 4 threads alive and ready to send emails.</li>
 *   <li>Max size 10 — spin up extra threads during heavy approval bursts.</li>
 *   <li>Queue capacity 50 — buffer tasks if all 10 threads are busy.</li>
 *   <li>Prefix "email-" — makes the threads easy to identify in thread dumps.</li>
 * </ul>
 */
@Configuration
public class AsyncConfig {

    /**
     * Creates and starts the thread pool executor used by all {@code @Async} methods
     * that specify {@code "appTaskExecutor"} as their executor name.
     *
     * @return a fully initialised ThreadPoolTaskExecutor ready to accept tasks
     */
    @Bean(name = "appTaskExecutor")
    public Executor appTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("email-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(10);
        exec.initialize();
        return exec;
    }
}