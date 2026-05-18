package com.college.gatepass.service;

import com.college.gatepass.dto.Dtos;
import com.college.gatepass.entity.ApprovalLog;
import com.college.gatepass.entity.GatePass;

/**
 * Static helper methods that convert JPA entity objects into DTO records
 * suitable for sending back in HTTP responses.
 *
 * <p>We keep mapping logic here (not inside the entity or DTO) so neither of
 * those layers knows about the other. Services call these methods and the
 * entities stay free of presentation concerns.
 */
public final class Mappers {

    private Mappers() {}

    /**
     * Converts a {@code GatePass} entity to a {@code GatePassResponse} DTO.
     * Null-safe: if the student or warden relationship is not loaded,
     * the corresponding ID and name fields in the response will be null.
     *
     * @param p the gate pass entity (may have lazily loaded relationships)
     * @return a DTO containing all pass fields safe to serialize to JSON
     */
    public static Dtos.GatePassResponse toResponse(GatePass p) {
        return new Dtos.GatePassResponse(
                p.getId(),
                p.getStudent() != null ? p.getStudent().getId()       : null,
                p.getStudent() != null ? p.getStudent().getFullName() : null,
                p.getReason(),
                p.getDestination(),
                p.getPassType(),
                p.getLeaveAt(),
                p.getReturnBy(),
                p.getStatus(),
                p.getWarden() != null ? p.getWarden().getId() : null,
                p.getDecisionNote(),
                p.getQrToken(),
                p.getUsedAt(),
                p.getReturnedAt(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getVersion(),
                // Student snapshot fields
                p.getDepartment(),
                p.getCourse(),
                p.getSemester(),
                p.getStudentMobile(),
                p.getParentMobile()
        );
    }

    /**
     * Converts an {@code ApprovalLog} entity to an {@code ApprovalLogDto}.
     *
     * @param l the approval log row from the database
     * @return a DTO containing all log fields safe to serialize to JSON
     */
    public static Dtos.ApprovalLogDto toLog(ApprovalLog l) {
        return new Dtos.ApprovalLogDto(
                l.getId(),
                l.getActorId(),
                l.getAction(),
                l.getFromStatus(),
                l.getToStatus(),
                l.getNote(),
                l.getIpAddress(),
                l.getCreatedAt()
        );
    }
}