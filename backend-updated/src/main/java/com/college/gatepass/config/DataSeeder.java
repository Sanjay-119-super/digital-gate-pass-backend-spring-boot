package com.college.gatepass.config;

import com.college.gatepass.entity.Role;
import com.college.gatepass.entity.User;
import com.college.gatepass.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Runs once at application startup to ensure the system has at least one
 * admin account to log in with.
 *
 * <p>This is a safety net in addition to the SQL seed in {@code V1__init.sql}.
 * If Flyway already inserted the admin user, the existence check prevents a
 * duplicate email error.
 *
 * <p><strong>Important:</strong> The default password is {@code Admin@1234}.
 * Change it immediately after the first login via the admin profile endpoint.
 * In production, also set the {@code ADMIN_EMAIL} and {@code ADMIN_PASSWORD}
 * environment variables.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    /**
     * Checks whether the default admin user exists and creates it if not.
     * Called automatically by Spring Boot after the application context is ready.
     *
     * @param args application arguments passed at startup (not used)
     */
    @Override
    public void run(ApplicationArguments args) {
        String adminEmail = System.getenv().getOrDefault("ADMIN_EMAIL", "admin@gatepass.edu");

        if (users.existsByEmail(adminEmail)) {
            log.info("DataSeeder: admin user already exists — skipping seed");
            return;
        }

        String rawPassword = System.getenv().getOrDefault("ADMIN_PASSWORD", "Admin@1234");

        User admin = User.builder()
                .email(adminEmail)
                .passwordHash(encoder.encode(rawPassword))
                .fullName("System Administrator")
                .active(true)
                .roles(Set.of(Role.ADMIN, Role.WARDEN))
                .build();

        users.save(admin);

        log.warn("╔══════════════════════════════════════════════════════════╗");
        log.warn("║  DataSeeder created a default admin account.             ║");
        log.warn("║  Email   : {}                        ║", adminEmail);
        log.warn("║  Password: {} (CHANGE THIS NOW!)          ║", rawPassword);
        log.warn("╚══════════════════════════════════════════════════════════╝");
    }
}