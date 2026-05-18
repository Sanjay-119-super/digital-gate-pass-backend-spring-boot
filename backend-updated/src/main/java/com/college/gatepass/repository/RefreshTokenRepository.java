package com.college.gatepass.repository;

import com.college.gatepass.entity.RefreshToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Database operations for the {@code refresh_tokens} table.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Looks up a refresh token by its SHA-256 hash.
     * We never store the raw token, so this is the only way to find it.
     *
     * @param hash the SHA-256 hex digest of the raw refresh token string
     * @return the matching token row, or empty if it does not exist (invalid / already used)
     */
    Optional<RefreshToken> findByTokenHash(String hash);

    /**
     * Deletes all refresh tokens belonging to a user.
     * Called on logout to invalidate all sessions for that user.
     *
     * @param userId the ID of the user whose tokens to delete
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}