package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.SarStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Suspicious Activity Reports
 */
@Repository
public interface SuspiciousActivityReportRepository extends JpaRepository<SuspiciousActivityReport, Long> {

    /**
     * Find SARs by status
     */
    List<SuspiciousActivityReport> findByStatus(SarStatus status);

    /**
     * Count SARs by status
     */
    long countByStatus(SarStatus status);

    /**
     * Find SARs due for filing soon
     */
    List<SuspiciousActivityReport> findByFilingDeadlineBeforeAndStatusNot(LocalDateTime date, SarStatus status);

    /**
     * Find SARs created within a window (for reporting)
     */
    List<SuspiciousActivityReport> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // PSP filtering
    long countByPspIdAndStatus(Long pspId, SarStatus status);

    List<SuspiciousActivityReport> findByPspIdAndCreatedAtBetween(Long pspId, LocalDateTime start, LocalDateTime end);

    // HOK-39 fix: filter SAR list by PSP
    List<SuspiciousActivityReport> findByPspId(Long pspId);
    List<SuspiciousActivityReport> findByPspIdAndStatus(Long pspId, SarStatus status);

    // HOK-61: PSP-scoped count for count/not-exported endpoint
    long countByPspId(Long pspId);

    // Used by SarContentGenerationService to enrich the rendered narrative with
    // a "related SARs in the same case" count.
    List<SuspiciousActivityReport> findByComplianceCase_Id(Long complianceCaseId);
    long countByComplianceCase_Id(Long complianceCaseId);

    // -----------------------------------------------------------------------
    // Dashboard SAR-filing-SLA aggregates
    // -----------------------------------------------------------------------

    /** Count SARs that have been filed (filed_at IS NOT NULL) since :since. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(s) FROM SuspiciousActivityReport s " +
        "WHERE s.filedAt IS NOT NULL AND s.filedAt >= :since")
    long countFiledSince(@org.springframework.data.repository.query.Param("since") LocalDateTime since);

    /**
     * Count SARs filed within {@code slaDays} of the linked case being opened.
     * Used as the numerator of the SAR filing SLA percentage. The case opened
     * time is joined from the {@code compliance_cases.created_at} column.
     */
    @org.springframework.data.jpa.repository.Query(value =
        "SELECT COUNT(*) FROM suspicious_activity_reports s " +
        "JOIN compliance_cases c ON c.id = s.case_id " +
        "WHERE s.filed_at IS NOT NULL " +
        "  AND s.filed_at >= :since " +
        "  AND s.filed_at <= c.created_at + (:slaDays * INTERVAL '1 day')",
        nativeQuery = true)
    long countFiledWithinSla(
        @org.springframework.data.repository.query.Param("since") LocalDateTime since,
        @org.springframework.data.repository.query.Param("slaDays") int slaDays);
}
