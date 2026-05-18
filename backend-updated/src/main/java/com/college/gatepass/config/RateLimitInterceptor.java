package com.college.gatepass.config;

import com.college.gatepass.exception.ApiException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits the number of auth-endpoint calls (login, register, refresh) per client IP.
 *
 * <p>This protects against brute-force password attacks and token-refresh flooding.
 * Each unique IP address gets its own token bucket. The bucket is refilled gradually
 * (greedy refill), so a burst of requests drains it quickly but the client can try
 * again after a short wait.
 *
 * <p>Algorithm: Token Bucket (Bucket4j).
 * <ul>
 *   <li>Capacity: {@code app.rate-limit.auth-requests-per-minute} tokens (default 10).</li>
 *   <li>Refill: the full capacity is restored every 60 seconds (1 token per 6 s).</li>
 *   <li>When a request arrives and the bucket is empty, the client receives
 *       a 429 Too Many Requests response.</li>
 * </ul>
 *
 * <p>Buckets are kept in a {@code ConcurrentHashMap} in memory. In a multi-instance
 * deployment, use Bucket4j with a Redis backend instead so all nodes share the
 * same counters.
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** Maximum auth requests an IP can make per minute before being throttled. */
    @Value("${app.rate-limit.auth-requests-per-minute:10}")
    private int requestsPerMinute;

    /**
     * One token bucket per client IP address.
     * {@code ConcurrentHashMap} handles concurrent access from multiple threads.
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Called by Spring MVC before every request that passes through this interceptor.
     * Extracts the client IP, looks up (or creates) its bucket, and tries to consume
     * one token. Returns false (blocks the request) if the bucket is empty.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response (not modified by this interceptor)
     * @param handler  the controller method that will handle this request (not used here)
     * @return true if the request is allowed through; false if it should be blocked
     * @throws ApiException 429 if the client has exceeded the rate limit
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        String ip = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, this::newBucket);

        if (bucket.tryConsume(1)) {
            return true;   // Request is allowed
        }

        log.warn("Rate limit exceeded for IP {} on path {}", ip, request.getRequestURI());
        throw ApiException.tooManyRequests(
                "Too many requests. You have exceeded " + requestsPerMinute
                + " auth attempts per minute. Please wait and try again.");
    }

    /**
     * Creates a new token bucket for a given IP address.
     * The bucket starts full and refills at a rate of one request every
     * {@code 60 / requestsPerMinute} seconds.
     *
     * @param ip the IP address key (used only for logging if needed)
     * @return a new Bucket4j bucket configured for this application's rate limit
     */
    private Bucket newBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.greedy(requestsPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Extracts the real client IP address from the request.
     * Checks the {@code X-Forwarded-For} header first (set by reverse proxies
     * like Nginx), then falls back to the direct remote address.
     *
     * @param request the HTTP request to extract the IP from
     * @return the client's IP address as a string
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may be a comma-separated list; take the first (client) IP
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}