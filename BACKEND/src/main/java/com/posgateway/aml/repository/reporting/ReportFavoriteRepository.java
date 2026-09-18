package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.ReportFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportFavoriteRepository extends JpaRepository<ReportFavorite, Long> {

    List<ReportFavorite> findByUserIdOrderByDisplayOrderAscCreatedAtDesc(Long userId);

    Optional<ReportFavorite> findByUserIdAndReportIdAndPspId(Long userId, Long reportId, Long pspId);

    Optional<ReportFavorite> findByUserIdAndReportCodeAndPspId(Long userId, String reportCode, Long pspId);

    void deleteByUserIdAndReportIdAndPspId(Long userId, Long reportId, Long pspId);

    void deleteByUserIdAndReportCodeAndPspId(Long userId, String reportCode, Long pspId);
}
