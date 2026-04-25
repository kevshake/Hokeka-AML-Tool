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
}
