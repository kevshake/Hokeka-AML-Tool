package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Compliance Cases
 */
@Repository
public interface ComplianceCaseRepository extends JpaRepository<ComplianceCase, Long> {

    /**
     * Find cases by status (Paginated)
     */
    org.springframework.data.domain.Page<ComplianceCase> findByStatus(CaseStatus status,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Find all cases (Paginated)
     */
    org.springframework.data.domain.Page<ComplianceCase> findAll(org.springframework.data.domain.Pageable pageable);

    /**
     * Find cases by status
     */
    List<ComplianceCase> findByStatus(CaseStatus status);

    /**
     * Find cases by merchant
     */
    List<ComplianceCase> findByMerchantId(Long merchantId);

    /**
     * Find cases assigned to user
     */
    List<ComplianceCase> findByAssignedTo_Id(Long userId);

    /**
     * Find open cases by priority
     */
    List<ComplianceCase> findByStatusAndPriorityOrderByCreatedAtAsc(CaseStatus status, CasePriority priority);

    /**
     * Count open cases by status
     */
    long countByStatus(CaseStatus status);

    /**
     * Count by priority
     */
    long countByPriority(CasePriority priority);

    /**
     * Count by status and priority
     */
    long countByStatusAndPriority(CaseStatus status, CasePriority priority);

    /**
     * Count by merchant and status
     */
    long countByMerchantIdAndStatus(Long merchantId, CaseStatus status);

    /**
     * Count by merchant and priority
     */
    long countByMerchantIdAndPriority(Long merchantId, CasePriority priority);

    /**
     * Find overdue cases
     */
    List<ComplianceCase> findBySlaDeadlineBeforeAndStatusNot(LocalDateTime date, CaseStatus status);

    /**
     * Find cases created within a window (for reporting)
     */
    List<ComplianceCase> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // PSP filtering
    long countByPspIdAndStatus(Long pspId, CaseStatus status);

    long countByPspIdAndPriority(Long pspId, CasePriority priority);

    long countByPspIdAndStatusAndPriority(Long pspId, CaseStatus status, CasePriority priority);

    List<ComplianceCase> findByPspIdAndCreatedAtBetween(Long pspId, LocalDateTime start, LocalDateTime end);

    // Count cases by assigned user and status list
    long countByAssignedTo_IdAndStatusIn(Long userId, List<CaseStatus> statuses);

    // Find last assignment time for a user
    java.util.Optional<ComplianceCase> findTop1ByAssignedTo_IdOrderByAssignedAtDesc(Long userId);

    // Find cases by status list
    List<ComplianceCase> findByStatusIn(List<CaseStatus> statuses);

    // Find cases by queue and status
    List<ComplianceCase> findByQueueAndStatus(com.posgateway.aml.entity.compliance.CaseQueue queue, CaseStatus status);

    // Count cases by queue and status
    long countByQueueAndStatus(com.posgateway.aml.entity.compliance.CaseQueue queue, CaseStatus status);

    // Find cases by status not equal
    List<ComplianceCase> findByStatusNot(CaseStatus status);

    // Find cases by PSP and status not equal
    List<ComplianceCase> findByPspIdAndStatusNot(Long pspId, CaseStatus status);

    /**
     * Find cases by PSP and Status (Paginated)
     */
    org.springframework.data.domain.Page<ComplianceCase> findByPspIdAndStatus(Long pspId, CaseStatus status,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Find all cases for PSP (Paginated)
     */
    org.springframework.data.domain.Page<ComplianceCase> findByPspId(Long pspId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Count all cases for PSP
     */
    long countByPspId(Long pspId);

    /**
     * Batch status counts for a PSP — one GROUP BY query replaces N×countByPspIdAndStatus calls.
     * Returns [{status, count}] rows.
     */
    @Query(value = "SELECT status, COUNT(*) FROM compliance_cases WHERE psp_id = :pspId GROUP BY status",
           nativeQuery = true)
    List<Object[]> countByPspIdGroupByStatus(@Param("pspId") Long pspId);

    /**
     * Batch priority counts for a PSP — replaces N×countByPspIdAndPriority calls.
     */
    @Query(value = "SELECT priority, COUNT(*) FROM compliance_cases WHERE psp_id = :pspId GROUP BY priority",
           nativeQuery = true)
    List<Object[]> countByPspIdGroupByPriority(@Param("pspId") Long pspId);

    /**
     * Global batch status counts (admin view).
     */
    @Query(value = "SELECT status, COUNT(*) FROM compliance_cases GROUP BY status",
           nativeQuery = true)
    List<Object[]> countAllGroupByStatus();

    /**
     * Global batch priority counts (admin view).
     */
    @Query(value = "SELECT priority, COUNT(*) FROM compliance_cases GROUP BY priority",
           nativeQuery = true)
    List<Object[]> countAllGroupByPriority();

    /**
     * Compliance team workload — open-case counts grouped by assignee for
     * a given PSP (the "team" is everyone with the same {@code psp_id}).
     * Returns rows of {@code [user_id (BIGINT), full_name (TEXT), count (BIGINT)]};
     * the {@code full_name} column is built as {@code first || ' ' || last}
     * and only includes users who currently have at least one open case.
     */
    @Query(value = "SELECT u.id, COALESCE(u.first_name || ' ' || u.last_name, u.username) AS full_name, COUNT(c.id) " +
            "FROM compliance_cases c " +
            "JOIN users u ON u.id = c.assigned_to_user_id" +
            "WHERE c.psp_id = :pspId " +
            "  AND c.status IN ('NEW','ASSIGNED','IN_PROGRESS','PENDING_REVIEW','ESCALATED','PENDING_INFO') " +
            "GROUP BY u.id, full_name " +
            "ORDER BY COUNT(c.id) DESC",
           nativeQuery = true)
    List<Object[]> countOpenCasesByAssigneeForPsp(@Param("pspId") Long pspId);

    /**
     * Open-case count for a specific assignee within a PSP.
     */
    @Query(value = "SELECT COUNT(*) FROM compliance_cases " +
            "WHERE assigned_to_user_id = :userId AND psp_id = :pspId " +
            "  AND status IN ('NEW','ASSIGNED','IN_PROGRESS','PENDING_REVIEW','ESCALATED','PENDING_INFO')",
           nativeQuery = true)
    long countOpenByAssigneeAndPsp(@Param("userId") Long userId, @Param("pspId") Long pspId);

    /**
     * Compliance team workload — open-case counts grouped by assignee
     * across all PSPs (admin view). Same shape as
     * {@link #countOpenCasesByAssigneeForPsp(Long)}.
     */
    @Query(value = "SELECT u.id, COALESCE(u.first_name || ' ' || u.last_name, u.username) AS full_name, COUNT(c.id) " +
            "FROM compliance_cases c " +
            "JOIN users u ON u.id = c.assigned_to_user_id " +
            "WHERE c.status IN ('NEW','ASSIGNED','IN_PROGRESS','PENDING_REVIEW','ESCALATED','PENDING_INFO') " +
            "GROUP BY u.id, full_name " +
            "ORDER BY COUNT(c.id) DESC",
           nativeQuery = true)
    List<Object[]> countOpenCasesByAssignee();

    // -----------------------------------------------------------------------
    // Dashboard aggregates (DashboardController)
    // -----------------------------------------------------------------------

    /** Count cases whose status is in :statuses and were resolved within [start, end). */
    @Query("SELECT COUNT(c) FROM ComplianceCase c " +
           "WHERE c.status IN :statuses " +
           "AND c.resolvedAt IS NOT NULL " +
           "AND c.resolvedAt >= :start AND c.resolvedAt < :end")
    long countByStatusInAndResolvedAtBetween(@Param("statuses") List<CaseStatus> statuses,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /** Count any open cases (used as denominator for closure rate). */
    @Query("SELECT COUNT(c) FROM ComplianceCase c WHERE c.status IN :statuses")
    long countByStatusIn(@Param("statuses") List<CaseStatus> statuses);

    /**
     * Daily resolved-case counts for the sparkline. Returns rows of
     * [date (java.sql.Date), count (Long)] grouped by DATE(resolved_at).
     */
    @Query(value = "SELECT DATE(c.resolved_at) AS d, COUNT(*) AS cnt " +
                   "FROM compliance_cases c " +
                   "WHERE c.resolved_at IS NOT NULL " +
                   "  AND c.resolved_at >= :start AND c.resolved_at < :end " +
                   "GROUP BY DATE(c.resolved_at) ORDER BY d", nativeQuery = true)
    List<Object[]> getDailyResolvedCounts(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /** Cases created within a window (for SAR-filing-SLA denominator). */
    long countByCreatedAtAfter(LocalDateTime since);
}
