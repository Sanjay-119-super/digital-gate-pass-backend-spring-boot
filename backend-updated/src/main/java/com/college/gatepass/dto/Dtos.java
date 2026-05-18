package com.college.gatepass.dto;

import com.college.gatepass.entity.PassStatus;
import com.college.gatepass.entity.PassType;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Set;

/**
 * A single holder class for all Data Transfer Objects (DTOs) used in the API.
 *
 * <p>We group them here so the controller and service imports stay short.
 * Every DTO is a Java {@code record} — immutable, compact, and auto-generates
 * equals/hashCode/toString. Validation annotations on records work with
 * Spring's {@code @Valid} / {@code @Validated} in the same way as on classes.
 */
public final class Dtos {

    // Private constructor — this class is just a namespace; never instantiate it.
    private Dtos() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Auth
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Body the client sends to POST /api/auth/login.
     *
     * @param email    the user's email address (must be a valid email format)
     * @param password the user's raw password (min 6 chars)
     */
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6) String password
    ) {}

    /**
     * Body the client sends to POST /api/auth/register.
     *
     * @param email        unique email for the new account
     * @param password     raw password (will be BCrypt-hashed before storing)
     * @param fullName     display name (e.g. "Rahul Sharma")
     * @param phone        optional mobile number for SMS alerts
     * @param enrollmentNo college roll number — required for STUDENT accounts
     * @param hostel       hostel block name — required for STUDENT accounts
     * @param roomNo       room number inside the hostel
     * @param roles        set of role strings ("STUDENT", "WARDEN", "SECURITY", "ADMIN")
     */
    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 72) String password,
            @NotBlank @Size(max = 150) String fullName,
            String phone,
            String enrollmentNo,
            String hostel,
            String roomNo,
            String department,
            String course,
            Integer semester,
            String studentMobile,
            String parentMobile,
            @NotEmpty Set<@NotBlank String> roles
    ) {}

    /**
     * Body the client sends to POST /api/auth/refresh.
     *
     * @param refreshToken the opaque refresh token string received at login
     */
    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    /**
     * Response returned after a successful login, register, or token refresh.
     *
     * @param accessToken  short-lived JWT the client puts in the Authorization header
     * @param refreshToken long-lived opaque token used to get a new access token
     * @param userId       the logged-in user's database ID
     * @param email        the logged-in user's email
     * @param roles        set of role names the user holds (e.g. ["STUDENT"])
     */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            Long userId,
            String email,
            Set<String> roles
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Gate Pass
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Body the student sends to POST /api/passes to create a new gate pass request.
     *
     * @param reason      why the student needs to leave (e.g. "Medical check-up")
     * @param destination where they are going (e.g. "District Hospital, Pilani")
     * @param passType    category: DAY, NIGHT, EMERGENCY, MEDICAL, or HOME
     * @param leaveAt     planned departure time (UTC instant)
     * @param returnBy    deadline to return by (must be after leaveAt)
     */
    public record CreatePassRequest(
            @NotBlank @Size(max = 500) String reason,
            @NotBlank @Size(max = 255) String destination,
            @NotNull PassType passType,
            @NotNull
            @FutureOrPresent
            Instant leaveAt,
            String department,
            String course,
            Integer semester,
            String studentMobile,
            String parentMobile,
            @NotNull Instant returnBy
    ) {}

    /**
     * Body sent by a warden to POST /api/passes/{id}/approve or /reject.
     *
     * @param note explanation for the decision (required on rejection, optional on approval)
     */
    public record DecideRequest(
            @Size(max = 500) String note
    ) {}

    /**
     * The full gate pass data returned to the client in all pass-related responses.
     *
     * @param id            database ID of the pass
     * @param studentId     ID of the student who owns this pass
     * @param studentName   display name of the student
     * @param reason        why the student needs to leave
     * @param destination   where the student is going
     * @param passType      category of the pass
     * @param leaveAt       planned departure time
     * @param returnBy      deadline to return by
     * @param status        current lifecycle state
     * @param wardenId      ID of the warden who decided (null if still pending)
     * @param decisionNote  the warden's note (null if still pending)
     * @param qrToken       the UUID used as the QR code lookup key (null until approved)
     * @param usedAt        when the student was scanned OUT (null if not yet used)
     * @param returnedAt    when the student was scanned IN (null if not yet returned)
     * @param createdAt     when the request was submitted
     * @param updatedAt     when the pass was last updated
     * @param version       JPA optimistic-lock version (useful for debugging 409s)
     */
    public record GatePassResponse(
            Long id,
            Long studentId,
            String studentName,
            String reason,
            String destination,
            PassType passType,
            Instant leaveAt,
            Instant returnBy,
            PassStatus status,
            Long wardenId,
            String decisionNote,
            String qrToken,
            Instant usedAt,
            Instant returnedAt,
            Instant createdAt,
            Instant updatedAt,
            int version,
            // Student snapshot — values at the time of pass creation
            String department,
            String course,
            Integer semester,
            String studentMobile,
            String parentMobile
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Approval Log
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * One entry in the pass audit history returned by GET /api/passes/{id}/history.
     *
     * @param id          log row ID
     * @param actorId     who triggered this action
     * @param action      what happened (CREATE, APPROVE, REJECT, CHECK_OUT, etc.)
     * @param fromStatus  the pass status before this action
     * @param toStatus    the pass status after this action
     * @param note        any note attached to this action
     * @param ipAddress   the client IP that made the request
     * @param createdAt   when this action happened
     */
    public record ApprovalLogDto(
            Long id,
            Long actorId,
            String action,
            String fromStatus,
            String toStatus,
            String note,
            String ipAddress,
            Instant createdAt
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Scan / Verify (used by Security Guard)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Body the security guard's app sends to POST /api/verify/scan.
     *
     * @param qrToken the token string read from the QR code
     */
    public record ScanRequest(
            @NotBlank String qrToken
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // QR Code
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returned when the client requests the QR code image for an approved pass.
     *
     * @param passId    the gate pass this QR code belongs to
     * @param qrToken   the token encoded inside the QR image
     * @param imageBase64 the QR code PNG image encoded as a Base64 string
     * @param scanUrl   the full URL that the QR code points to (for deep linking)
     */
    public record QrCodeResponse(
            Long passId,
            String qrToken,
            String imageBase64,
            String scanUrl
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Admin / Reporting
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Dashboard summary counts returned to the admin / security dashboard.
     *
     * @param totalPasses     all passes ever created
     * @param pendingCount    passes waiting for warden decision right now
     * @param approvedToday   passes approved in the last 24 hours
     * @param currentlyOut    passes in USED state (student is outside campus)
     * @param expiredToday    passes that expired today
     */
    public record DashboardStats(
            long totalPasses,
            long pendingCount,
            long approvedToday,
            long currentlyOut,
            long expiredToday
    ) {}

    /*----------new changes start-------*/
    // OTP verification request
    public record VerifyOtpRequest(
            @NotBlank @Email
            String email,
            @NotBlank
            String otp
    ) {}

    // Simple message response (use also for forgot/reset)
    public record MessageResponse(String message) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}
    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 6) String newPassword
    ) {}
    /*------New changes end--------*/
}