package com.college.gatepass.repository;

import com.college.gatepass.entity.ApprovalLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Database operations for the {@code approval_logs} table.
 */
public interface ApprovalLogRepository extends JpaRepository<ApprovalLog, Long> {

    /**
     * Returns all audit log entries for a specific gate pass, oldest first.
     * Used to show the full decision timeline for a pass.
     *
     * @param passId the ID of the gate pass whose history to load
     * @return a list of log entries ordered from earliest to latest
     */
    List<ApprovalLog> findByPassIdOrderByCreatedAtAsc(Long passId);
}