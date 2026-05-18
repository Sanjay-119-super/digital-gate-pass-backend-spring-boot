package com.college.gatepass.service;

import com.college.gatepass.dto.Dtos;
import org.springframework.beans.factory.annotation.Value;
import com.college.gatepass.entity.RefreshToken;
import com.college.gatepass.entity.Role;
import com.college.gatepass.entity.User;
import com.college.gatepass.exception.ApiException;
import com.college.gatepass.repository.RefreshTokenRepository;
import com.college.gatepass.repository.UserRepository;
import com.college.gatepass.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles user registration, login, and token refresh.
 *
 * <p>Refresh tokens rotate on every use: when the client presents a refresh token,
 * that token is immediately revoked and a brand-new one is issued.
 * This means a stolen refresh token can only be used once before it is invalidated.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshes;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuthenticationManager authManager;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;

    /**
     * Registers a new user account and returns a token pair so the user is
     * immediately logged in without a separate login step.
     *
     * @param r the registration data (email, password, name, roles, etc.)
     * @return a token response with access + refresh tokens and the user's ID
     * @throws ApiException 409 if the email or enrollment number is already registered
     */
    @Transactional
    public Dtos.MessageResponse register(Dtos.RegisterRequest r) {
        /*------New changes------*/
        // Email uniqueness check
        if (users.existsByEmail(r.email()))
            throw ApiException.conflict("Email already registered");

        // Enrollment number uniqueness check (if provided)
        if (r.enrollmentNo() != null && users.existsByEnrollmentNo(r.enrollmentNo()))
            throw ApiException.conflict("Enrollment number already registered");

        // Convert string roles to enum and validate
        Set<Role> roles = r.roles().stream()
                .map(roleStr -> {
                    Role role = Role.valueOf(roleStr.toUpperCase());
                    // Block self-registration of privileged roles
                    if (role == Role.ADMIN || role == Role.WARDEN || role == Role.SECURITY) {
                        throw ApiException.forbidden(
                                "Cannot self-register with role: " + role +
                                        ". Contact an administrator.");
                    }
                    return role;
                })
                .collect(Collectors.toSet());

        // Default to STUDENT if no role provided
        if (roles.isEmpty()) {
            roles = Set.of(Role.STUDENT);
        }

        // Build user entity
        User u = User.builder()
                .email(r.email().toLowerCase().strip())
                .passwordHash(encoder.encode(r.password()))
                .fullName(r.fullName())
                .phone(r.phone())
                .enrollmentNo(r.enrollmentNo())
                .hostel(r.hostel())
                .roomNo(r.roomNo())
                .department(r.department())
                .course(r.course())
                .semester(r.semester())
                .studentMobile(r.studentMobile())
                .parentMobile(r.parentMobile())
                .active(true)
                .emailVerified(false)   // not verified yet
                .roles(roles)
                .build();

        // Generate 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(1000000));
        u.setEmailOtp(otp);
        u.setOtpExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));

        users.save(u);

        // Send verification email asynchronously
        emailService.sendVerificationEmail(u, otp);

        // Return message instead of tokens
        return new Dtos.MessageResponse(
                "Registration successful. A 6-digit OTP has been sent to your email. Please verify within 10 minutes."
        );
    }

    /**
     * Authenticates a user with email and password, then returns a new token pair.
     *
     * @param req the login credentials (email and password)
     * @return a token response with access + refresh tokens
     * @throws ApiException 401 if the email does not exist or the password is wrong
     */
    @Transactional
    public Dtos.TokenResponse login(Dtos.LoginRequest req) {
        /*------New changes-----*/
        User u = users.findByEmail(req.email())
                .orElseThrow(() -> new ApiException(401, "Invalid email or password"));
        if (!u.isEmailVerified()) {
            throw new ApiException(403, "Email not verified. Please verify your email first.");
        }

        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password())
            );
        } catch (BadCredentialsException e) {
            // Do not reveal whether the email or password was wrong
            throw new ApiException(401, "Invalid email or password");
        }
        User uu = users.findByEmail(req.email()).orElseThrow();
        return issueTokens(u);
    }

    /**
     * Exchanges a valid refresh token for a brand-new access + refresh token pair.
     * The old refresh token is revoked immediately (token rotation).
     *
     * @param rawRefreshToken the raw refresh token string sent by the client
     * @return a new token pair with fresh expiry times
     * @throws ApiException 401 if the token is invalid, revoked, or expired
     */
    @Transactional
    public Dtos.TokenResponse refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);

        RefreshToken rt = refreshes.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(401, "Invalid refresh token"));

        if (rt.isRevoked() || rt.getExpiresAt().isBefore(Instant.now()))
            throw new ApiException(401, "Refresh token has expired. Please log in again.");

        // Revoke the old token immediately — single-use enforcement
        rt.setRevoked(true);
        refreshes.save(rt);

        User u = users.findById(rt.getUserId()).orElseThrow();
        return issueTokens(u);
    }

    /**
     * Revokes all refresh tokens for a user, effectively logging them out of
     * all devices at once.
     *
     * @param userId the ID of the user to log out
     */
    @Transactional
    public void logoutAll(Long userId) {
        refreshes.deleteByUserId(userId);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Generates a new access + refresh token pair for the given user and
     * saves the new refresh token to the database.
     *
     * @param u the user to issue tokens for
     * @return the token response DTO with both tokens and user info
     */
    private Dtos.TokenResponse issueTokens(User u) {
        Set<String> roleNames = u.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        String accessToken   = jwt.generateAccess(u.getId(), u.getEmail(), roleNames);
        String rawRefresh    = jwt.randomRefreshToken();

        refreshes.save(RefreshToken.builder()
                .userId(u.getId())
                .tokenHash(sha256(rawRefresh))
                .expiresAt(Instant.now().plus(jwt.getRefreshTtlDays(), ChronoUnit.DAYS))
                .revoked(false)
                .build());

        return new Dtos.TokenResponse(accessToken, rawRefresh, u.getId(), u.getEmail(), roleNames);
    }

    /**
     * Computes the SHA-256 hex digest of a string.
     * Used to hash refresh tokens before storing or looking them up in the database.
     *
     * @param input the plain text to hash (e.g. a raw refresh token)
     * @return a 64-character lowercase hex string representing the SHA-256 hash
     */
    /*static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }*/

    static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)); // ← Explicit charset!
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
    /*-------new changes start-------*/
    @Transactional
    public void verifyEmail(String email, String otp) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.isEmailVerified()) {
            throw ApiException.conflict("Email already verified");
        }
        if (!otp.equals(user.getEmailOtp())) {
            throw ApiException.badRequest("Invalid OTP");
        }
        if (user.getOtpExpiry().isBefore(Instant.now())) {
            throw ApiException.badRequest("OTP expired. Please register again.");
        }

        user.setEmailVerified(true);
        user.setEmailOtp(null);
        user.setOtpExpiry(null);
        users.save(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("No account found with this email"));

        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(Instant.now().plus(30, ChronoUnit.MINUTES));
        users.save(user);

        // Build reset link (frontend will handle)
        String resetLink = baseUrl + "/reset-password?token=" + token;
        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetLink);
    }
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = users.findByResetToken(token)
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired reset token"));

        if (user.getResetTokenExpiry().isBefore(Instant.now())) {
            throw ApiException.badRequest("Reset token expired. Request a new one.");
        }

        user.setPasswordHash(encoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        users.save(user);

        // Invalidate all refresh tokens for security
        refreshes.deleteByUserId(user.getId());
    }

}