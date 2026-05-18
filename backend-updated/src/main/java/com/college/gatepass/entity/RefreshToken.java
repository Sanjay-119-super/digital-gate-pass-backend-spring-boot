package com.college.gatepass.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Stores one refresh token for a user so they can get new access tokens without
 * logging in again.
 *
 * <p>Security note: we never store the raw token string.
 * Only the SHA-256 hash is saved here.
 * When the client sends back the refresh token, we hash it again and look it up.
 * This way, even if someone reads the database, they cannot reuse the tokens.
 *
 * <p>Tokens are single-use (token rotation): when a refresh is performed,
 * the old row is marked {@code revoked = true} and a new row is created.
 * If the same token is used twice, that is a sign of theft — the system
 * can revoke all tokens for that user.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The ID of the user this token belongs to. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * SHA-256 hex digest of the raw refresh token string.
     * Must be unique — each issued token hashes to a different value.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    /** The UTC time after which this token is no longer valid. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * True means this token has already been used (rotated) or
     * was explicitly revoked (e.g. on logout).
     * A revoked token must never be accepted.
     */
    @Builder.Default
    private boolean revoked = false;

    /** When this token row was created. Set once in {@code @PrePersist}. */
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * Sets {@code createdAt} to the current UTC time just before the first insert.
     */
    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}