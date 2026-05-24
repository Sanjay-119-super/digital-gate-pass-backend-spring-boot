package com.college.gatepass.config;

import com.college.gatepass.security.AppUserDetailsService;
import com.college.gatepass.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Sets up Spring Security for the whole application.
 *
 * <p>Key choices made here:
 * <ul>
 *   <li>CSRF is disabled — this is a stateless REST API; CSRF protection is
 *       only needed for cookie-based browser sessions.</li>
 *   <li>Sessions are STATELESS — every request must carry a JWT; no server-side
 *       session is created.</li>
 *   <li>The {@code JwtAuthFilter} runs before Spring's own authentication filter
 *       so it can populate the security context first.</li>
 *   <li>{@code @EnableMethodSecurity} lets us use {@code @PreAuthorize} annotations
 *       on individual controller or service methods for fine-grained access control.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AppUserDetailsService userDetailsService;

    /**
     * Defines which URLs are public and which require authentication/roles.
     *
     * @param http the HttpSecurity builder provided by Spring
     * @return the built and configured security filter chain
     * @throws Exception if any configuration step fails (checked by Spring)
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF — not needed for stateless JWT APIs
                .csrf(AbstractHttpConfigurer::disable)

                // Enable CORS with our configuration bean below
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // No session state — every request is authenticated via JWT
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Route-level access rules
                .authorizeHttpRequests(auth -> auth

                        // Public endpoints — no token needed
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                        // QR verify GET — security guards may use a simple mobile link
                        .requestMatchers(HttpMethod.GET, "/api/verify/**").hasAnyRole("SECURITY", "ADMIN")

                        // Warden-only endpoints
                        .requestMatchers("/api/passes/pending").hasAnyRole("WARDEN", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/passes/*/approve").hasAnyRole("WARDEN", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/passes/*/reject").hasAnyRole("WARDEN", "ADMIN")

                        // Admin-only endpoints
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/export/**").hasAnyRole("ADMIN", "WARDEN")   // ✅ fixed
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Everything else requires at least a valid token
                        .anyRequest().authenticated()
                )

                // Use our custom JWT filter instead of form login
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    /**
     * Creates the authentication provider that Spring Security uses during login.
     * It loads the user from the database using {@code AppUserDetailsService} and
     * checks the password using BCrypt.
     *
     * @return a fully configured DaoAuthenticationProvider
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the {@code AuthenticationManager} as a Spring bean so that
     * {@code AuthService.login()} can call {@code authManager.authenticate()}.
     *
     * @param config the AuthenticationConfiguration provided by Spring Boot auto-config
     * @return the application's AuthenticationManager
     * @throws Exception if the manager cannot be created (rare Spring internal error)
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * The BCrypt password encoder used throughout the application.
     * Strength 10 is a good balance of security and speed.
     *
     * @return a BCryptPasswordEncoder with strength 10
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * Configures CORS so the React / mobile frontend can call this API from a
     * different origin. In production, replace "*" with your actual frontend domain.
     *
     * @return a CORS configuration source with permissive settings for development
     */
    /*@Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow any origin in development — tighten this in production
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }*/
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",                          // local dev
                "https://gatepassjuedu.vercel.app"                // ✅ YOUR NEW FRONTEND
        ));
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }


}