package com.college.gatepass.entity;

/**
 * The category of gate pass a student is applying for.
 * This helps wardens prioritise (e.g. EMERGENCY passes can be fast-tracked).
 */
public enum PassType {

    /** Leave and return within the same day; normal daytime outing. */
    DAY,

    /** Permission to stay outside the hostel overnight. */
    NIGHT,

    /** Urgent unplanned exit; warden should act on this immediately. */
    EMERGENCY,

    /** Medical appointment or hospital visit. */
    MEDICAL,

    /** Student is going home for holidays or a weekend. */
    HOME
}