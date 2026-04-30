package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, Long> {

    List<ReportDefinition> findByReportId(Long reportId);

    Optional<ReportDefinition> findByReportIdAndIsActiveTrue(Long reportId);

    Optional<ReportDefinition> findByReportIdAndVersion(Long reportId, Integer version);

    @Modifying
    @Query("UPDATE ReportDefinition rd SET rd.isActive = false WHERE rd.report.id = :reportId AND rd.id != :activeId")
    void deactivateOtherVersions(@Param("reportId") Long reportId, @Param("activeId") Long activeId);

    @Query("SELECT MAX(rd.version) FROM ReportDefinition rd WHERE rd.report.id = :reportId")
    Integer findMaxVersionByReportId(@Param("reportId") Long reportId);
}
