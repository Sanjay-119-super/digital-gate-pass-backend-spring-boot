package com.college.gatepass.controller;

import com.college.gatepass.dto.Dtos;
import com.college.gatepass.exception.ApiException;
import com.college.gatepass.service.GatePassService;
import com.college.gatepass.service.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST endpoints for the gate pass lifecycle.
 *
 * <p>Students create and cancel their own passes.
 * Wardens list pending passes and approve or reject them.
 * Both roles can read pass details and history.
 * Anyone with a valid JWT can fetch their own pass's QR code image.
 */
@Tag(name = "Gate Passes", description = "Create, manage, and decide gate passes")
@RestController
@RequestMapping("/api/passes")
@RequiredArgsConstructor
public class GatePassController {

    private final GatePassService passService;
    private final QrCodeService qrCodeService;

    // ── Student endpoints ────────────────────────────────────────────────────

    /**
     * Creates a new gate pass request for the currently logged-in student.
     *
     * @param userId  the logged-in student's ID (from JWT via Spring Security)
     * @param request the pass details (reason, destination, type, leave/return times)
     * @return 201 Created with the saved pass DTO (status = PENDING)
     */
    @Operation(summary = "Student: submit a new gate pass request")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public ResponseEntity<Dtos.GatePassResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody Dtos.CreatePassRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(passService.create(userId, request));
    }

    /**
     * Lists all gate passes belonging to the currently logged-in student, newest first.
     *
     * @param userId the logged-in student's ID
     * @param page   page number (0-based, default 0)
     * @param size   records per page (default 20)
     * @return a page of the student's own gate passes
     */
    @Operation(summary = "Student: list my gate passes")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me")
    public ResponseEntity<Page<Dtos.GatePassResponse>> myPasses(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(passService.listForStudent(userId, pageable));
    }

    /**
     * Cancels the student's own PENDING gate pass.
     *
     * @param userId  the logged-in student's ID
     * @param passId  the ID of the pass to cancel
     * @param request the HTTP request (used to extract the client IP for the audit log)
     * @return 200 OK with the updated pass DTO (status = CANCELLED)
     */
    @Operation(summary = "Student: cancel my pending gate pass")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Dtos.GatePassResponse> cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long passId,
            HttpServletRequest request) {
        return ResponseEntity.ok(passService.cancel(passId, userId, getIp(request)));
    }

    // ── Warden endpoints ─────────────────────────────────────────────────────

    /**
     * Lists all gate passes currently waiting for a warden decision (status = PENDING).
     *
     * @param page page number (0-based, default 0)
     * @param size records per page (default 20)
     * @return a page of PENDING pass DTOs
     */
    @Operation(summary = "Warden: list all pending gate pass requests")
    @PreAuthorize("hasAnyRole('WARDEN','ADMIN')")
    @GetMapping("/pending")
    public ResponseEntity<Page<Dtos.GatePassResponse>> pending(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        return ResponseEntity.ok(passService.listPending(pageable));
    }

    /**
     * Approves a pending gate pass and generates its QR token.
     * Publishes a {@code PassDecidedEvent} so the student is emailed asynchronously.
     *
     * @param wardenId the logged-in warden's ID
     * @param passId   the ID of the pass to approve
     * @param body     optional note the warden wants to attach
     * @param request  the HTTP request for IP extraction
     * @return 200 OK with the updated pass DTO (status = APPROVED, qrToken set)
     */
    @Operation(summary = "Warden: approve a gate pass")
    @PreAuthorize("hasAnyRole('WARDEN','ADMIN')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<Dtos.GatePassResponse> approve(
            @AuthenticationPrincipal Long wardenId,
            @PathVariable("id") Long passId,
            @RequestBody(required = false) Dtos.DecideRequest body,
            HttpServletRequest request) {
        String note = (body != null) ? body.note() : null;
        return ResponseEntity.ok(passService.approve(passId, wardenId, note, getIp(request)));
    }

    /**
     * Rejects a pending gate pass.
     *
     * @param wardenId the logged-in warden's ID
     * @param passId   the ID of the pass to reject
     * @param body     the rejection note (shown to the student in the email)
     * @param request  the HTTP request for IP extraction
     * @return 200 OK with the updated pass DTO (status = REJECTED)
     */
    @Operation(summary = "Warden: reject a gate pass")
    @PreAuthorize("hasAnyRole('WARDEN','ADMIN')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<Dtos.GatePassResponse> reject(
            @AuthenticationPrincipal Long wardenId,
            @PathVariable("id") Long passId,
            @RequestBody(required = false) Dtos.DecideRequest body,
            HttpServletRequest request) {
        String note = (body != null) ? body.note() : null;
        return ResponseEntity.ok(passService.reject(passId, wardenId, note, getIp(request)));
    }

    // ── Shared endpoints ─────────────────────────────────────────────────────

    /**
     * Returns the full details of a single gate pass by ID.
     * Any authenticated user with a valid JWT can call this, but in practice
     * students should only access their own passes (enforcement can be added with a check).
     *
     * @param id the gate pass ID
     * @return 200 OK with the pass DTO, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Dtos.GatePassResponse> get(
            @PathVariable Long id,
            @AuthenticationPrincipal Long requesterId,
            Authentication authentication) {

        Dtos.GatePassResponse pass = passService.get(id);

        // Student sirf apna pass dekh sakta hai
        // Warden/Admin/Security sab dekh sakte hain
        boolean isPrivileged = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_WARDEN") ||
                        a.equals("ROLE_ADMIN") ||
                        a.equals("ROLE_SECURITY"));

        if (!isPrivileged && !pass.studentId().equals(requesterId)) {
            throw ApiException.forbidden("You can only view your own passes");
        }

        return ResponseEntity.ok(pass);
    }

    /**
     * Returns the full audit trail (approval log) for a gate pass, oldest event first.
     * Students can only fetch history for their own passes.
     * Wardens, Admins, and Security can fetch any pass's history.
     *
     * @param passId    the gate pass whose history to fetch
     * @param requesterId the logged-in user's ID (from JWT)
     * @param authentication Spring Security context — used to check roles
     * @return 200 OK with an ordered list of log entry DTOs, or 403 if not authorized
     */
    @Operation(summary = "Get audit history for a gate pass")
    @GetMapping("/{id}/history")
    public ResponseEntity<List<Dtos.ApprovalLogDto>> history(
            @PathVariable("id") Long passId,
            @AuthenticationPrincipal Long requesterId,
            Authentication authentication) {

        // Fetch pass first to know who owns it
        Dtos.GatePassResponse pass = passService.get(passId);

        boolean isPrivileged = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_WARDEN") ||
                        a.equals("ROLE_ADMIN") ||
                        a.equals("ROLE_SECURITY"));

        if (!isPrivileged && !pass.studentId().equals(requesterId)) {
            throw ApiException.forbidden("You can only view history for your own passes");
        }

        return ResponseEntity.ok(passService.history(passId));
    }

    /**
     * Returns the QR code image (PNG) for an approved gate pass as raw bytes.
     * Students can only download QR for their own passes.
     * Wardens, Admins, and Security can download any pass's QR.
     *
     * @param passId      the ID of the approved gate pass
     * @param requesterId the logged-in user's ID (from JWT)
     * @param authentication Spring Security context — used to check roles
     * @return 200 OK with {@code Content-Type: image/png} bytes,
     *         or 403 if not authorized, or 409 if the pass is not yet approved
     */
    @Operation(summary = "Download QR code PNG for an approved gate pass")
    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrImage(
            @PathVariable("id") Long passId,
            @AuthenticationPrincipal Long requesterId,
            Authentication authentication) {

        Dtos.GatePassResponse pass = passService.get(passId);

        boolean isPrivileged = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_WARDEN") ||
                        a.equals("ROLE_ADMIN") ||
                        a.equals("ROLE_SECURITY"));

        if (!isPrivileged && !pass.studentId().equals(requesterId)) {
            throw ApiException.forbidden("You can only download QR for your own passes");
        }

        if (pass.qrToken() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        long expiryEpoch = pass.returnBy().getEpochSecond();
        byte[] png = qrCodeService.generateQrPng(pass.qrToken(), expiryEpoch);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    /**
     * Returns the QR code as a JSON object containing the Base64-encoded image
     * and the signed URL — useful for mobile apps that want to display it inline.
     * Students can only access QR data for their own passes.
     *
     * @param passId      the ID of the approved gate pass
     * @param requesterId the logged-in user's ID (from JWT)
     * @param authentication Spring Security context — used to check roles
     * @return a QrCodeResponse DTO with Base64 image and scan URL, or 403 if not authorized
     */
    @Operation(summary = "Get QR code details (Base64 + URL) for an approved gate pass")
    @GetMapping("/{id}/qr/data")
    public ResponseEntity<Dtos.QrCodeResponse> qrData(
            @PathVariable("id") Long passId,
            @AuthenticationPrincipal Long requesterId,
            Authentication authentication) {

        Dtos.GatePassResponse pass = passService.get(passId);

        boolean isPrivileged = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_WARDEN") ||
                        a.equals("ROLE_ADMIN") ||
                        a.equals("ROLE_SECURITY"));

        if (!isPrivileged && !pass.studentId().equals(requesterId)) {
            throw ApiException.forbidden("You can only access QR data for your own passes");
        }

        if (pass.qrToken() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        long expiryEpoch  = pass.returnBy().getEpochSecond();
        String base64     = qrCodeService.generateQrBase64(pass.qrToken(), expiryEpoch);
        String signedUrl  = qrCodeService.buildSignedUrl(pass.qrToken(), expiryEpoch);

        return ResponseEntity.ok(
                new Dtos.QrCodeResponse(passId, pass.qrToken(), base64, signedUrl)
        );
    }

    // ── Private helper ───────────────────────────────────────────────────────

    /**
     * Extracts the real client IP from the request, checking the
     * {@code X-Forwarded-For} header for requests coming through a reverse proxy.
     *
     * @param request the HTTP request
     * @return the client IP address string
     */
    private String getIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].strip()
                : request.getRemoteAddr();
    }
}