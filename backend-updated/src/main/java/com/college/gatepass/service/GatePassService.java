package com.college.gatepass.service;

import com.college.gatepass.dto.Dtos;
import com.college.gatepass.entity.*;
import com.college.gatepass.event.PassDecidedEvent;
import com.college.gatepass.exception.ApiException;
import com.college.gatepass.repository.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.cache.annotation.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GatePassService {
    private final GatePassRepository passes;
    private final UserRepository users;
    private final ApprovalLogRepository logs;
    private final ApplicationEventPublisher events;

    @Transactional
    public Dtos.GatePassResponse create(Long studentId, Dtos.CreatePassRequest r) {
        if (r.leaveAt().isBefore(Instant.now().plusSeconds(300))) {
            throw ApiException.badRequest("Leave time must be at least 5 minutes in the future");
        }
        User student = users.findById(studentId)
                .orElseThrow(() -> ApiException.notFound("Student not found"));
        if (!student.getRoles().contains(Role.STUDENT))
            throw ApiException.forbidden("Only students can create passes");

        GatePass p = GatePass.builder()
                .student(student).reason(r.reason()).destination(r.destination())
                .passType(r.passType()).leaveAt(r.leaveAt()).returnBy(r.returnBy())
                .department(r.department()).course(r.course()).semester(r.semester())
                .studentMobile(r.studentMobile()).parentMobile(r.parentMobile())
                .status(PassStatus.PENDING).build();
        passes.save(p);
        logs.save(ApprovalLog.builder().passId(p.getId()).actorId(studentId)
                .action("CREATE").toStatus("PENDING").build());

        // Force initialization of lazy student proxy before returning DTO
        Hibernate.initialize(p.getStudent());

        return Mappers.toResponse(p);
    }

    @Transactional(readOnly = true)
    public Page<Dtos.GatePassResponse> listForStudent(Long studentId, Pageable pageable) {
        return passes.findByStudent_Id(studentId, pageable).map(Mappers::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<Dtos.GatePassResponse> listPending(Pageable pageable) {
        return passes.findByStatus(PassStatus.PENDING, pageable).map(Mappers::toResponse);
    }

    @Transactional(readOnly = true)
    public Dtos.GatePassResponse get(Long id) {
        GatePass p = passes.findById(id)
                .orElseThrow(() -> ApiException.notFound("Pass not found"));
        // Ensure all lazy proxies are loaded before leaving the transaction
        Hibernate.initialize(p.getStudent());
        if (p.getWarden() != null) {
            Hibernate.initialize(p.getWarden());
        }
        return Mappers.toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<Dtos.ApprovalLogDto> history(Long passId) {
        return logs.findByPassIdOrderByCreatedAtAsc(passId).stream()
                .map(Mappers::toLog).collect(Collectors.toList());
    }

    @CacheEvict(value = "verify", key = "#result.qrToken", condition = "#result.qrToken != null")
    @Transactional
    public Dtos.GatePassResponse approve(Long passId, Long wardenId, String note, String ip) {
        GatePass p = lock(passId);
        if (p.getStatus() != PassStatus.PENDING)
            throw ApiException.conflict("Pass is not pending (current: " + p.getStatus() + ")");
        User warden = users.findById(wardenId)
                .orElseThrow(() -> ApiException.notFound("Warden not found"));
        p.setStatus(PassStatus.APPROVED);
        p.setWarden(warden);
        p.setDecisionNote(note);
        p.setQrToken(UUID.randomUUID().toString().replace("-", ""));
        passes.save(p);
        logs.save(ApprovalLog.builder().passId(passId).actorId(wardenId).action("APPROVE")
                .fromStatus("PENDING").toStatus("APPROVED").note(note).ipAddress(ip).build());

        // Initialize lazy proxies before publishing event and returning DTO
        Hibernate.initialize(p.getStudent());
        Hibernate.initialize(p.getWarden());

        events.publishEvent(new PassDecidedEvent(p, "APPROVE"));
        return Mappers.toResponse(p);
    }

    @Transactional
    public Dtos.GatePassResponse reject(Long passId, Long wardenId, String note, String ip) {
        GatePass p = lock(passId);
        if (p.getStatus() != PassStatus.PENDING)
            throw ApiException.conflict("Pass is not pending");
        User warden = users.findById(wardenId)
                .orElseThrow(() -> ApiException.notFound("Warden not found"));
        p.setStatus(PassStatus.REJECTED);
        p.setWarden(warden);
        p.setDecisionNote(note);
        passes.save(p);
        logs.save(ApprovalLog.builder().passId(passId).actorId(wardenId).action("REJECT")
                .fromStatus("PENDING").toStatus("REJECTED").note(note).ipAddress(ip).build());

        Hibernate.initialize(p.getStudent());
        Hibernate.initialize(p.getWarden());

        events.publishEvent(new PassDecidedEvent(p, "REJECT"));
        return Mappers.toResponse(p);
    }

    @Transactional
    public Dtos.GatePassResponse cancel(Long passId, Long studentId, String ip) {
        GatePass p = lock(passId);
        if (!p.getStudent().getId().equals(studentId))
            throw ApiException.forbidden("Not your pass");
        if (p.getStatus() != PassStatus.PENDING)
            throw ApiException.conflict("Only pending passes can be cancelled");
        p.setStatus(PassStatus.CANCELLED);
        passes.save(p);
        logs.save(ApprovalLog.builder().passId(passId).actorId(studentId).action("CANCEL")
                .fromStatus("PENDING").toStatus("CANCELLED").ipAddress(ip).build());

        Hibernate.initialize(p.getStudent());
        // No warden yet, so no need to initialize warden
        return Mappers.toResponse(p);
    }

    @Cacheable(value = "verify", key = "#qrToken")
    @Transactional(readOnly = true)
    public Dtos.GatePassResponse verifyLookup(String qrToken) {
        GatePass p = passes.findByQrToken(qrToken)
                .orElseThrow(() -> ApiException.notFound("Invalid pass token"));
        Hibernate.initialize(p.getStudent());
        if (p.getWarden() != null) {
            Hibernate.initialize(p.getWarden());
        }
        return Mappers.toResponse(p);
    }

    @CacheEvict(value = "verify", key = "#qrToken")
    @Transactional
    public Dtos.GatePassResponse markUsed(String qrToken, Long securityId, String ip) {
        GatePass p = passes.findByQrToken(qrToken)
                .orElseThrow(() -> ApiException.notFound("Invalid pass token"));
        Instant now = Instant.now();
        if (p.getStatus() == PassStatus.APPROVED) {
            if (now.isAfter(p.getReturnBy())) {
                p.setStatus(PassStatus.EXPIRED);
                passes.save(p);
                logs.save(ApprovalLog.builder().passId(p.getId()).actorId(securityId)
                        .action("EXPIRE").fromStatus("APPROVED").toStatus("EXPIRED").ipAddress(ip).build());
                throw ApiException.conflict("Pass expired");
            }
            p.setStatus(PassStatus.USED);
            p.setUsedAt(now);
            passes.save(p);
            logs.save(ApprovalLog.builder().passId(p.getId()).actorId(securityId)
                    .action("CHECK_OUT").fromStatus("APPROVED").toStatus("USED").ipAddress(ip).build());
        } else if (p.getStatus() == PassStatus.USED) {
            p.setStatus(PassStatus.RETURNED);
            p.setReturnedAt(now);
            passes.save(p);
            logs.save(ApprovalLog.builder().passId(p.getId()).actorId(securityId)
                    .action("CHECK_IN").fromStatus("USED").toStatus("RETURNED").ipAddress(ip).build());
        } else {
            throw ApiException.conflict("Pass cannot be used in state " + p.getStatus());
        }

        Hibernate.initialize(p.getStudent());
        if (p.getWarden() != null) {
            Hibernate.initialize(p.getWarden());
        }
        return Mappers.toResponse(p);
    }

    private GatePass lock(Long id) {
        return passes.findByIdWithLock(id)
                .orElseThrow(() -> ApiException.notFound("Pass not found"));
    }
}