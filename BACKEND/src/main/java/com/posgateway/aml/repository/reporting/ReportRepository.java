package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.Report;
import com.posgateway.aml.entity.reporting.ReportCategory;
import com.posgateway.aml.entity.reporting.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByReportCode(String reportCode);

    List<Report> findByReportCategoryAndEnabledTrue(ReportCategory category);

    List<Report> findByReportTypeAndEnabledTrue(ReportType type);

    Page<Report> findByEnabledTrue(Pageable pageable);

    @Query("SELECT r FROM Report r WHERE r.enabled = true AND " +
           "(:category IS NULL OR r.reportCategory = :category) AND " +
           "(:type IS NULL OR r.reportType = :type) AND " +
           "(:search IS NULL OR LOWER(r.reportName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(r.reportCode) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Report> findByFilters(@Param("category") ReportCategory category,
                                  @Param("type") ReportType type,
                                  @Param("search") String search,
                                  Pageable pageable);

    @Query("SELECT r.reportCategory, COUNT(r) FROM Report r WHERE r.enabled = true GROUP BY r.reportCategory")
    List<Object[]> countByCategory();

    boolean existsByReportCode(String reportCode);
}
