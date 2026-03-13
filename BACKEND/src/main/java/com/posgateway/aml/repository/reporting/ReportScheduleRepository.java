package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.ReportSchedule;
import com.posgateway.aml.entity.reporting.ScheduleFrequency;
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
public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, Long> {

    Page<ReportSchedule> findByReportId(Long reportId, Pageable pageable);

    Page<ReportSchedule> findByPspId(Long pspId, Pageable pageable);

    Page<ReportSchedule> findByIsActiveTrue(Pageable pageable);

    List<ReportSchedule> findByIsActiveTrueAndNextRunAtBefore(LocalDateTime time);

    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.isActive = true AND " +
           "(:reportId IS NULL OR rs.report.id = :reportId)")
    Page<ReportSchedule> findByFilters(@Param("reportId") Long reportId, Pageable pageable);

    long countByReportIdAndIsActiveTrue(Long reportId);

    long countByPspIdAndIsActiveTrue(Long pspId);

    Optional<ReportSchedule> findByIdAndPspId(Long id, Long pspId);
}
