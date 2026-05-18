package com.college.gatepass.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A single gate pass request created by a student.
 *
 * <p>Lifecycle: PENDING → APPROVED / REJECTED → USED → RETURNED / EXPIRED.
 * Every state change is also written to {@code ApprovalLog} for a full audit trail.
 *
 * <p>The {@code @Version} field provides JPA optimistic locking.
 * If two wardens try to approve the same pass at exactly the same time,
 * the second request will get a 409 Conflict response instead of silently
 * overwriting the first change.
 */
@Entity
@Table(name = "gate_passes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatePass {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The student who created this pass request.
     * Loaded lazily — only fetched when explicitly accessed.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    /** Why the student needs to leave campus (shown to the warden). */
    @Column(nullable = false, length = 500)
    private String reason;

    /** Where the student is going (e.g. "City Hospital", "Home - Jaipur"). */
    @Column(nullable = false)
    private String destination;

    /** What kind of pass this is — DAY, NIGHT, EMERGENCY, MEDICAL, HOME. */
    @Enumerated(EnumType.STRING)
    @Column(name = "pass_type", nullable = false)
    private PassType passType;

    /** When the student plans to leave campus. */
    @Column(name = "leave_at", nullable = false)
    private Instant leaveAt;

    /**
     * When the student must return by.
     * The scheduler automatically marks the pass EXPIRED if not scanned by this time.
     */
    @Column(name = "return_by", nullable = false)
    private Instant returnBy;

    /** Current lifecycle state of this pass. Starts as PENDING. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PassStatus status = PassStatus.PENDING;

    /**
     * The warden who made the approve/reject decision.
     * Null while the pass is still PENDING.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warden_id")
    private User warden;

    /** Optional note left by the warden when approving or rejecting. */
    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    /**
     * A random UUID string generated when the pass is approved.
     * This value is what the QR code encodes (as part of a signed URL).
     * Null until the pass is approved.
     */
    @Column(name = "qr_token", unique = true)
    private String qrToken;

    /**
     * When the security guard scans the pass on the way OUT.
     * Null until the student's first scan.
     */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Timestamp when the security guard scanned the pass on the way IN (return). */
    @Column(name = "returned_at")
    private Instant returnedAt;

    // ── Student snapshot fields (captured at pass-creation time) ─────────────
    // Stored here so historical records remain accurate even if the student
    // later updates their profile.

    /** Student's department at the time of pass creation. */
    @Column(name = "department", length = 100)
    private String department;

    /** Student's course/programme at the time of pass creation. */
    @Column(name = "course", length = 100)
    private String course;

    /** Student's semester at the time of pass creation. */
    @Column(name = "semester")
    private Integer semester;

    /** Student's mobile number at the time of pass creation (10 digits). */
    @Column(name = "student_mobile", length = 15)
    private String studentMobile;

    /** Parent/guardian mobile number at the time of pass creation (10 digits). */
    @Column(name = "parent_mobile", length = 15)
    private String parentMobile;

    /**
     * JPA optimistic locking version counter.
     * Incremented automatically on every update; prevents concurrent writes
     * from silently overwriting each other.
     */
    @Version
    private int version;

    /** When this record was first inserted. Set once by {@code @PrePersist}. */
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** Updated automatically on every save by {@code @PreUpdate}. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Called by JPA just before the first INSERT.
     * Sets both {@code createdAt} and {@code updatedAt} to the current UTC time.
     */
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    /**
     * Called by JPA just before every UPDATE.
     * Keeps {@code updatedAt} fresh so we always know when the pass last changed.
     */
    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}