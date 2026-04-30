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
}

