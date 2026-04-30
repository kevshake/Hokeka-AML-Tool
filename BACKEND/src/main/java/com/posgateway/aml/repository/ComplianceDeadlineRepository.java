package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Compliance Deadlines
 */
@Repository
public interface ComplianceDeadlineRepository extends JpaRepository<ComplianceDeadline, Long> {

    /**
     * Find deadlines in date range that are not completed
     */
    List<ComplianceDeadline> findByDeadlineDateBetweenAndCompletedFalse(
            LocalDateTime start, LocalDateTime end);

    /**
     * Find overdue deadlines
     */
    List<ComplianceDeadline> findByDeadlineDateBeforeAndCompletedFalse(LocalDateTime date);

    /**
     * Find deadlines by type
     */
    List<ComplianceDeadline> findByDeadlineType(String deadlineType);

    /**
     * PSP-scoped variants: include the PSP's own deadlines AND any global ones (psp_id IS NULL).
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT d FROM ComplianceDeadline d " +
            "WHERE d.completed = false " +
            "  AND d.deadlineDate BETWEEN :start AND :end " +
            "  AND (d.pspId = :pspId OR d.pspId IS NULL) " +
            "ORDER BY d.deadlineDate ASC")
    List<ComplianceDeadline> findUpcomingForPsp(
            @org.springframework.data.repository.query.Param("pspId") Long pspId,
            @org.springframework.data.repository.query.Param("start") LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") LocalDateTime end);

    @org.springframework.data.jpa.repository.Query(
            "SELECT d FROM ComplianceDeadline d " +
            "WHERE d.completed = false " +
            "  AND d.deadlineDate < :now " +
            "  AND (d.pspId = :pspId OR d.pspId IS NULL) " +
            "ORDER BY d.deadlineDate ASC")
    List<ComplianceDeadline> findOverdueForPsp(
            @org.springframework.data.repository.query.Param("pspId") Long pspId,
            @org.springframework.data.repository.query.Param("now") LocalDateTime now);
}

