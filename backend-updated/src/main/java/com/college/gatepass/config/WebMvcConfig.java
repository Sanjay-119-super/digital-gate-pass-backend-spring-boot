package com.college.gatepass.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers Spring MVC interceptors.
 *
 * <p>The {@code RateLimitInterceptor} is applied only to {@code /api/auth/**} paths
 * (login, register, refresh). All other endpoints are excluded because they are
 * already protected by JWT authentication — an attacker without a token cannot
 * spam them usefully.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    /**
     * Adds the rate-limit interceptor to the Spring MVC interceptor chain,
     * scoped to authentication endpoints only.
     *
     * @param registry the Spring MVC interceptor registry to add interceptors to
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/auth/**");
    }
}