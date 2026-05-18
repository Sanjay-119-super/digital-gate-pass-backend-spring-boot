package com.college.gatepass.service;

import com.college.gatepass.entity.ApprovalLog;
import com.college.gatepass.entity.GatePass;
import com.college.gatepass.entity.PassStatus;
import com.college.gatepass.repository.ApprovalLogRepository;
import com.college.gatepass.repository.GatePassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * A scheduled background job that automatically expires gate passes whose
 * {@code returnBy} deadline has passed but whose status is still APPROVED.
 *
 * <p>This covers the case where a student got their pass approved but never
 * actually showed up at the gate — or left and never came back through the scanner.
 *
 * <p>The job runs every hour. It finds all APPROVED passes where
 * {@code returnBy < now}, marks them as EXPIRED, writes an audit log entry,
 * and sends the student an expiry warning email.
 *
 * <p>The {@code @Scheduled} cron runs at the top of every hour.
 * You can change it to every 15 minutes by setting:
 * {@code fixedDelay = 900_000} instead of using a cron expression.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassExpiryScheduler {

    private final GatePassRepository passes;
    private final ApprovalLogRepository logs;
    private final EmailService emailService;

    /**
     * Finds all APPROVED passes whose {@code returnBy} time has passed and marks
     * them as EXPIRED. Runs automatically every hour at the top of the hour.
     *
     * <p>This method is transactional: if anything fails mid-run, no partial
     * updates are committed. Each pass is processed and saved individually,
     * so one bad row does not block the rest.
     */
    @Scheduled(cron = "0 0 * * * *")   // every hour at :00
    @Transactional
    public void expireOverduePasses() {
        Instant now = Instant.now();
        List<GatePass> overdue = passes.findExpiredPasses(PassStatus.APPROVED, now);

        if (overdue.isEmpty()) {
            log.debug("Expiry scheduler ran — no overdue passes found");
            return;
        }

        log.info("Expiry scheduler: found {} overdue APPROVED pass(es) to expire", overdue.size());

        for (GatePass p : overdue) {
            try {
                p.setStatus(PassStatus.EXPIRED);
                passes.save(p);

                logs.save(ApprovalLog.builder()
                        .passId(p.getId())
                        // actorId null = system-triggered action (no human actor)
                        .actorId(null)
                        .action("EXPIRE")
                        .fromStatus("APPROVED")
                        .toStatus("EXPIRED")
                        .note("Auto-expired by scheduler at " + now)
                        .build());

                // Send expiry warning email asynchronously — failure here
                // should not roll back the database update
                emailService.sendExpiryWarningEmail(p);

                log.info("Pass {} expired (returnBy was {})", p.getId(), p.getReturnBy());

            } catch (Exception e) {
                log.error("Failed to expire pass {}: {}", p.getId(), e.getMessage(), e);
                // Continue with the next pass — don't let one failure stop the rest
            }
        }
    }
}