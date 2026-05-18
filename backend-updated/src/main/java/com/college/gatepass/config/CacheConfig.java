package com.college.gatepass.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Sets up the Redis-backed Spring cache used for QR code verification lookups.
 *
 * <p>When the security guard scans a QR code, {@code GatePassService.verifyLookup()}
 * is annotated with {@code @Cacheable(value = "verify", key = "#qrToken")}.
 * The first scan hits the database; subsequent identical calls within the TTL
 * window are served from Redis and return in under 1 ms.
 *
 * <p>Cache entries expire after 5 minutes by default. Because pass status can
 * change (APPROVED → USED → RETURNED), every status-changing operation also
 * calls {@code @CacheEvict} to remove the stale entry immediately.
 *
 * <p>Values are serialised as JSON so they survive a Redis restart or a rolling
 * application deployment without needing a cache flush.
 */
@Configuration
@EnableCaching
@ConditionalOnBean(RedisConnectionFactory.class)
public class CacheConfig {

    /**
     * Creates the Redis cache manager with a 5-minute TTL and JSON serialisation.
     *
     * @param factory the Redis connection factory auto-configured by Spring Boot
     * @return a fully configured CacheManager backed by Redis
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // Entries expire after 5 minutes — prevents serving stale pass status
                .entryTtl(Duration.ofMinutes(5))
                // Store cache keys as plain strings (human-readable in Redis CLI)
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                // Store values as JSON (not Java serialisation) for portability
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()))
                // Do not cache null values — a missing pass should always hit the DB
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}