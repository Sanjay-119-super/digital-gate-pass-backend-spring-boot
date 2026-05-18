package com.college.gatepass.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents every person who can log in — student, warden, security guard, or admin.
 *
 * <p>Roles are stored in a separate {@code user_roles} join table so one user can
 * hold multiple roles (e.g. an admin who is also a warden).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user's email address; used as the login username.
     * Must be unique across the whole system.
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * BCrypt-hashed password. Never store or log the raw password.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** The user's display name shown on gate passes and emails. */
    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** Optional mobile number for SMS notifications (future feature). */
    private String phone;

    /**
     * College roll / enrollment number — only relevant for students.
     * Must be unique if provided.
     */
    @Column(name = "enrollment_no", unique = true)
    private String enrollmentNo;

    /** Name of the hostel block the student lives in. */
    private String hostel;

    /** Room number within the hostel. */
    @Column(name = "room_no")
    private String roomNo;

    /** Academic department of the student (e.g. "Computer Science"). */
    @Column(name = "department")
    private String department;

    /** Degree programme the student is enrolled in (e.g. "B.Tech"). */
    @Column(name = "course")
    private String course;

    /** Current semester (1–8). */
    @Column(name = "semester")
    private Integer semester;

    /** Student's own mobile number — 10 digits. */
    @Column(name = "student_mobile")
    private String studentMobile;

    /** Parent / guardian mobile number — 10 digits. */
    @Column(name = "parent_mobile")
    private String parentMobile;

/*---------------New changes start------------*/
    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "email_otp", length = 10)
    private String emailOtp;

    @Column(name = "otp_expiry")
    private Instant otpExpiry;

    @Column(name = "reset_token", unique = true)
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private Instant resetTokenExpiry;

    /*--------New changes end--------*/

    /**
     * Whether the account is active. Inactive accounts cannot log in.
     * Use this instead of hard-deleting users to preserve audit history.
     */
    @Builder.Default
    private boolean active = true;

    /** Timestamp when this user record was first created. Set once in {@code @PrePersist}. */
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * Set of roles this user holds. Loaded eagerly because Spring Security
     * needs them on every request to build the {@code GrantedAuthority} list.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    /**
     * Sets {@code createdAt} to the current UTC time just before the first
     * database insert. JPA calls this automatically.
     */
    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}