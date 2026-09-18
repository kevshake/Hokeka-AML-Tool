package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.ReportSavedFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportSavedFilterRepository extends JpaRepository<ReportSavedFilter, Long> {

    List<ReportSavedFilter> findByUserIdAndReportIdOrderByFilterNameAsc(Long userId, Long reportId);

    List<ReportSavedFilter> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<ReportSavedFilter> findByUserIdAndReportIdAndFilterName(Long userId, Long reportId, String filterName);
}
