package com.college.gatepass.controller;

import com.college.gatepass.dto.Dtos;
import com.college.gatepass.entity.PassStatus;
import com.college.gatepass.repository.GatePassRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Admin-only endpoints for the management dashboard.
 *
 * <p>Returns summary statistics used by the admin and security dashboards to show
 * real-time counts. All endpoints are restricted to users with the ADMIN role.
 */
@Tag(name = "Admin", description = "Admin-only management and reporting")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final GatePassRepository passes;

    /**
     * Returns a summary of gate pass counts for the admin dashboard.
     *
     * <p>Includes:
     * <ul>
     *   <li>Total passes ever created.</li>
     *   <li>Passes currently waiting for warden approval.</li>
     *   <li>Passes approved in the last 24 hours.</li>
     *   <li>Passes in USED state (student is currently outside campus).</li>
     *   <li>Passes that expired today.</li>
     * </ul>
     *
     * @return 200 OK with a {@code DashboardStats} DTO
     */
    @Operation(summary = "Get dashboard summary statistics")
    @GetMapping("/dashboard")
    public ResponseEntity<Dtos.DashboardStats> dashboard() {
        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);

        Dtos.DashboardStats stats = new Dtos.DashboardStats(
                passes.count(),
                passes.countByStatus(PassStatus.PENDING),
                passes.countByStatusAndUpdatedAtAfter(PassStatus.APPROVED, since24h),
                passes.countByStatus(PassStatus.USED),
                passes.countByStatusAndUpdatedAtAfter(PassStatus.EXPIRED, since24h)
        );

        return ResponseEntity.ok(stats);
    }
}