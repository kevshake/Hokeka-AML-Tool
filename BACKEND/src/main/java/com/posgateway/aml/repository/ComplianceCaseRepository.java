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
     * Returns [{case_status, count}] rows.
     */
    @Query(value = "SELECT case_status, COUNT(*) FROM compliance_cases WHERE psp_id = :pspId GROUP BY case_status",
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
    @Query(value = "SELECT case_status, COUNT(*) FROM compliance_cases GROUP BY case_status",
           nativeQuery = true)
    List<Object[]> countAllGroupByStatus();

    /**
     * Global batch priority counts (admin view).
     */
    @Query(value = "SELECT priority, COUNT(*) FROM compliance_cases GROUP BY priority",
           nativeQuery = true)
    List<Object[]> countAllGroupByPriority();
}
