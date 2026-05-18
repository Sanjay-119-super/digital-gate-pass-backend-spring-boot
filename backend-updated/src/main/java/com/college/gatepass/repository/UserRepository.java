package com.college.gatepass.repository;

import com.college.gatepass.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Database operations for the {@code users} table.
 *
 * <p>Spring Data JPA auto-implements all methods at runtime — no SQL needed.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their email address (used during login).
     *
     * @param email the email to search for (case-sensitive)
     * @return an Optional containing the user if found, or empty if not
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks whether an email is already registered (used during registration to
     * prevent duplicates).
     *
     * @param email the email to check
     * @return true if a user with this email already exists
     */
    boolean existsByEmail(String email);

    /**
     * Checks whether an enrollment number is already registered (used during
     * student registration).
     *
     * @param enrollmentNo the enrollment number to check
     * @return true if a student with this enrollment number already exists
     */
    boolean existsByEnrollmentNo(String enrollmentNo);

    Optional<User> findByResetToken(String resetToken);
}