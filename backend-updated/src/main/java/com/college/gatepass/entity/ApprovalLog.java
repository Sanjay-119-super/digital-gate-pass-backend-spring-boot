package com.college.gatepass.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One row in the audit log for a gate pass.
 *
 * <p>Every time a pass changes state (created, approved, rejected, scanned,
 * expired) we write one row here. These rows are never deleted or updated —
 * they are a permanent record of who did what and when.
 *
 * <p>We store {@code passId} and {@code actorId} as plain longs (not JPA
 * relationships) to keep the log table simple and insert-only.
 */
@Entity
@Table(name = "approval_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalLog {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The ID of the gate pass this log entry belongs to. */
    @Column(name = "pass_id", nullable = false)
    private Long passId;

    /**
     * The ID of the user who triggered this action (student, warden, security).
     * NULL when the action was performed by the system (e.g. the expiry scheduler).
     */
    @Column(name = "actor_id", nullable = true)
    private Long actorId;

    /**
     * A short keyword describing what happened.
     * Values: CREATE, APPROVE, REJECT, CANCEL, CHECK_OUT, CHECK_IN, EXPIRE.
     */
    @Column(nullable = false, length = 30)
    private String action;

    /** The status the pass was in before this action (null for CREATE). */
    @Column(name = "from_status", length = 20)
    private String fromStatus;

    /** The status the pass moved to after this action. */
    @Column(name = "to_status", length = 20)
    private String toStatus;

    /** Optional note explaining the decision (e.g. the warden's rejection reason). */
    @Column(length = 500)
    private String note;

    /**
     * The IP address of the client who made this request.
     * Useful for security investigations.
     */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** Exact UTC time this log row was written. Set once and never changed. */
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * Sets {@code createdAt} to now just before the first INSERT.
     * Because the field is marked {@code updatable = false},
     * JPA will never overwrite it in a later UPDATE.
     */
    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}