package com.college.gatepass.repository;

import com.college.gatepass.entity.GatePass;
import com.college.gatepass.entity.PassStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Database operations for the {@code gate_passes} table.
 */
public interface GatePassRepository extends JpaRepository<GatePass, Long> {

    /**
     * Returns a page of gate passes belonging to a specific student,
     * ordered by the database default (most recently created first when
     * combined with a {@code Pageable} sort).
     *
     * @param studentId the ID of the student whose passes to load
     * @param pageable  paging and sorting parameters from the request
     * @return a page of gate pass records for that student
     */
    Page<GatePass> findByStudent_Id(Long studentId, Pageable pageable);

    /**
     * Returns a page of gate passes that have the given status.
     * Used by the warden dashboard to list all PENDING passes.
     *
     * @param status   the status to filter by (e.g. PassStatus.PENDING)
     * @param pageable paging and sorting parameters
     * @return a page of matching gate pass records
     */
    Page<GatePass> findByStatus(PassStatus status, Pageable pageable);

    /**
     * Finds a single gate pass by its QR token string.
     * Used by the security guard scanner to look up a pass when a QR code is scanned.
     *
     * @param qrToken the unique QR token (UUID) printed on the pass
     * @return an Optional containing the pass if found, or empty if the token is invalid
     */
    Optional<GatePass> findByQrToken(String qrToken);

    /**
     * Finds all passes that are APPROVED but whose {@code returnBy} deadline
     * has already passed. Used by the expiry scheduler to auto-expire them.
     *
     * @param status the status to match (caller passes PassStatus.APPROVED)
     * @param now    the current UTC time — passes with returnBy before this are expired
     * @return a list of overdue passes that need to be marked EXPIRED
     */
    /*@Query("SELECT p FROM GatePass p WHERE p.status = :status AND p.returnBy < :now")
    List<GatePass> findExpiredPasses(@Param("status") PassStatus status,
                                     @Param("now") Instant now);*/

    @Query("SELECT p FROM GatePass p LEFT JOIN FETCH p.student WHERE p.status = :status AND p.returnBy < :now")
    List<GatePass> findExpiredPasses(@Param("status") PassStatus status,
                                     @Param("now") Instant now);

    /**
     * Counts passes by status — used for the admin dashboard summary numbers.
     *
     * @param status the status to count
     * @return number of passes currently in that status
     */
    long countByStatus(PassStatus status);

    /**
     * Counts passes approved after a given time — used for "approved today" stat.
     *
     * @param status the status to filter by (APPROVED)
     * @param since  the start of the time window (e.g. start of today in UTC)
     * @return number of passes approved since that time
     */
    @Query("SELECT COUNT(p) FROM GatePass p WHERE p.status = :status AND p.updatedAt >= :since")
    long countByStatusAndUpdatedAtAfter(@Param("status") PassStatus status,
                                        @Param("since") Instant since);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM GatePass p WHERE p.id = :id")
    Optional<GatePass> findByIdWithLock(@Param("id") Long id);
}