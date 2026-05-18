package com.college.gatepass.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and validates JWT access tokens using HMAC-SHA256.
 *
 * <p>Every JWT we issue contains:
 * <ul>
 *   <li>subject — the user's numeric database ID (as a string)</li>
 *   <li>email — the user's email address</li>
 *   <li>roles — the list of role names (e.g. ["STUDENT"])</li>
 *   <li>iat — issued-at timestamp</li>
 *   <li>exp — expiry timestamp (accessTtlMin minutes from now)</li>
 * </ul>
 *
 * <p>The token is signed with a secret key loaded from {@code app.jwt.secret}.
 * If anyone tampers with the token payload, the signature check will fail
 * and {@code parse()} will throw an exception.
 */
@Component
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-ttl-minutes}")
    private long accessTtlMin;

    /**
     * How many days a refresh token stays valid.
     * Exposed as a getter so {@code AuthService} can compute the expiry timestamp.
     */
    @Getter
    @Value("${app.jwt.refresh-ttl-days}")
    private long refreshTtlDays;

    /** The HMAC key built from the secret string. Built once at startup. */
    private SecretKey key;

    /**
     * Builds the signing key from the configured secret string.
     * Called automatically by Spring after all fields are injected.
     */
    @PostConstruct
    void init() {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a signed JWT access token for the given user.
     *
     * @param userId the user's database ID (stored as the JWT subject)
     * @param email  the user's email address (stored as a custom claim)
     * @param roles  the user's roles (stored as a custom "roles" claim)
     * @return a compact, signed JWT string the client puts in the Authorization header
     */
    public String generateAccess(Long userId, String email, Collection<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtlMin, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates a JWT string, returning its claims if the token is
     * valid and has not expired.
     *
     * @param token the raw JWT string from the Authorization header (without "Bearer ")
     * @return the claims (subject, email, roles, expiry) stored inside the token
     * @throws io.jsonwebtoken.JwtException if the token is invalid, expired, or tampered
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Generates a cryptographically random, opaque refresh token string.
     * It is two UUIDs joined by a dot — long enough to be unguessable.
     * We SHA-256 hash this before storing it in the database.
     *
     * @return a random opaque string to be used as a refresh token
     */
    public String randomRefreshToken() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }
}