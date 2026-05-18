package com.college.gatepass.entity;

/**
 * All roles a user can hold in the system.
 *
 * <p>A user can have more than one role (e.g. a warden who is also an admin).
 * Roles are stored as strings in the {@code user_roles} table.
 */
public enum Role {

    /** A college student who can apply for gate passes. */
    STUDENT,

    /** A hostel warden who approves or rejects student gate pass requests. */
    WARDEN,

    /** A security guard at the gate who scans QR codes. */
    SECURITY,

    /** A system administrator who manages users and sees all reports. */
    ADMIN
}