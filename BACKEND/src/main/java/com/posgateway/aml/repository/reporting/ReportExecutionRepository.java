package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.ExecutionStatus;
import com.posgateway.aml.entity.reporting.ReportExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportExecutionRepository extends JpaRepository<ReportExecution, Long> {

    Optional<ReportExecution> findByExecutionId(String executionId);

    Page<ReportExecution> findByTriggeredBy(Long triggeredBy, Pageable pageable);

    Page<ReportExecution> findByReportId(Long reportId, Pageable pageable);

    Page<ReportExecution> findByPspId(Long pspId, Pageable pageable);

    List<ReportExecution> findByStatus(ExecutionStatus status);

    @Query("SELECT re FROM ReportExecution re WHERE " +
           "(:reportId IS NULL OR re.report.id = :reportId) AND " +
           "(:pspId IS NULL OR re.pspId = :pspId) AND " +
           "(:status IS NULL OR re.status = :status) AND " +
           "(:dateFrom IS NULL OR re.createdAt >= :dateFrom) AND " +
           "(:dateTo IS NULL OR re.createdAt <= :dateTo)")
    Page<ReportExecution> findByFilters(@Param("reportId") Long reportId,
                                          @Param("pspId") Long pspId,
                                          @Param("status") ExecutionStatus status,
                                          @Param("dateFrom") LocalDateTime dateFrom,
                                          @Param("dateTo") LocalDateTime dateTo,
                                          Pageable pageable);

    @Query("SELECT re FROM ReportExecution re WHERE re.status = :status AND re.createdAt < :cutoffTime")
    List<ReportExecution> findStaleExecutions(@Param("status") ExecutionStatus status, 
                                                @Param("cutoffTime") LocalDateTime cutoffTime);

    long countByReportIdAndStatus(Long reportId, ExecutionStatus status);

    @Query("SELECT re FROM ReportExecution re WHERE re.report.id = :reportId " +
           "AND re.status = :status AND (:pspId IS NULL OR re.pspId = :pspId) " +
           "ORDER BY re.completedAt DESC, re.id DESC")
    List<ReportExecution> findLatestForSubmission(
            @Param("reportId") Long reportId,
            @Param("pspId") Long pspId,
            @Param("status") ExecutionStatus status,
            Pageable pageable);

    @Query("SELECT COUNT(re), " +
           "SUM(CASE WHEN re.status = com.posgateway.aml.entity.reporting.ExecutionStatus.COMPLETED THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN re.status = com.posgateway.aml.entity.reporting.ExecutionStatus.FAILED THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN re.status = com.posgateway.aml.entity.reporting.ExecutionStatus.PENDING THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN re.status = com.posgateway.aml.entity.reporting.ExecutionStatus.RUNNING THEN 1 ELSE 0 END), " +
           "COALESCE(AVG(re.executionTimeMs), 0), COALESCE(SUM(re.totalRecords), 0) " +
           "FROM ReportExecution re WHERE (:pspId IS NULL OR re.pspId = :pspId) " +
           "AND (:from IS NULL OR re.createdAt >= :from) " +
           "AND (:to IS NULL OR re.createdAt <= :to)")
    Object[] summarizeExecutions(
            @Param("pspId") Long pspId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
