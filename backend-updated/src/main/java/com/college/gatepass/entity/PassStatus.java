package com.college.gatepass.entity;

/**
 * All possible states a gate pass can be in during its lifecycle.
 *
 * <p>Valid transitions (enforced in {@code GatePassService}):
 * <pre>
 *  PENDING ──► APPROVED ──► USED ──► RETURNED
 *     │            │
 *     ▼            ▼
 *  REJECTED     EXPIRED
 *     │
 *     ▼
 *  CANCELLED
 * </pre>
 */
public enum PassStatus {

    /** The student has submitted the pass; waiting for a warden decision. */
    PENDING,

    /** A warden approved the pass; a QR code has been generated. */
    APPROVED,

    /** A warden rejected the pass. */
    REJECTED,

    /**
     * The student has left campus — the security guard scanned the QR code
     * on the way OUT.
     */
    USED,

    /**
     * The student has returned to campus — the security guard scanned the QR
     * code on the way IN.
     */
    RETURNED,

    /**
     * The pass was not used before its {@code return_by} timestamp and was
     * automatically marked expired by the scheduler.
     */
    EXPIRED,

    /** The student cancelled their own pending request before it was decided. */
    CANCELLED
}