package com.college.gatepass.controller;

import com.college.gatepass.dto.Dtos;
import com.college.gatepass.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for authentication: register, login, token refresh, and logout.
 *
 * <p>All paths under {@code /api/auth} are public (no JWT required).
 * The rate limiter in {@code RateLimitInterceptor} throttles these endpoints
 * to {@code app.rate-limit.auth-requests-per-minute} requests per IP per minute.
 */
@Tag(name = "Authentication", description = "Register, login, refresh, and logout")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Registers a new user account.
     * The user is immediately issued a token pair so they do not need to log in separately.
     *
     * @param request the registration data (email, password, name, roles, etc.)
     * @return 201 Created with the token pair and user info
     */
    @Operation(summary = "Register a new user account")
    @PostMapping("/register")
    public ResponseEntity<Dtos.MessageResponse> register(@Valid @RequestBody Dtos.RegisterRequest request) {
        Dtos.MessageResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    /**
     * Logs in with email and password, returning a new access + refresh token pair.
     *
     * @param request the login credentials
     * @return 200 OK with the token pair, or 401 if credentials are wrong
     */
    @Operation(summary = "Log in and receive JWT tokens")
    @PostMapping("/login")
    public ResponseEntity<Dtos.TokenResponse> login(@Valid @RequestBody Dtos.LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Exchanges a valid refresh token for a new access + refresh token pair.
     * The old refresh token is immediately revoked (token rotation).
     *
     * @param request the refresh token (sent in the request body, not a cookie)
     * @return 200 OK with a fresh token pair, or 401 if the refresh token is invalid/expired
     */
    @Operation(summary = "Refresh access token using a refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<Dtos.TokenResponse> refresh(@Valid @RequestBody Dtos.RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    /**
     * Logs out the currently authenticated user by revoking all their refresh tokens.
     * The current access token is not blacklisted (it is short-lived by design),
     * but any attempt to refresh it will fail immediately.
     *
     * @param userId the ID of the logged-in user, extracted from the JWT by Spring Security
     * @return 204 No Content on success
     */
    @Operation(summary = "Log out and revoke all refresh tokens")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logoutAll(userId);
        return ResponseEntity.noContent().build();
    }
    /*-----New changes-------*/
    @PostMapping("/verify-email")
    public ResponseEntity<Dtos.MessageResponse> verifyEmail(
            @Valid @RequestBody Dtos.VerifyOtpRequest request) {
        authService.verifyEmail(request.email(), request.otp());
        return ResponseEntity.ok(new Dtos.MessageResponse("Email verified. You can now log in."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Dtos.MessageResponse> forgotPassword(
            @Valid @RequestBody Dtos.ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(
                new Dtos.MessageResponse("If the email exists, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Dtos.MessageResponse> resetPassword(
            @Valid @RequestBody Dtos.ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(
                new Dtos.MessageResponse("Password reset successful. You can now log in."));
    }
}