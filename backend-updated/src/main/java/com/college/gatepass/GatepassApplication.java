package com.college.gatepass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Digital Gate Pass backend application.
 *
 * <p>Annotations enabled here:
 * <ul>
 *   <li>{@code @EnableCaching} — turns on Spring's cache abstraction so
 *       {@code @Cacheable} / {@code @CacheEvict} on services work with Redis.</li>
 *   <li>{@code @EnableAsync} — lets {@code @Async} methods (e.g. email sending)
 *       run on a separate thread pool without blocking the HTTP request.</li>
 *   <li>{@code @EnableScheduling} — activates the pass-expiry cron job
 *       defined in {@code PassExpiryScheduler}.</li>
 * </ul>
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableSpringDataWebSupport(
        pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO
)
public class GatepassApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments passed to the JVM (not used directly)
     */
    public static void main(String[] args) {
        SpringApplication.run(GatepassApplication.class, args);
    }
}