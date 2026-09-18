package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.ReportResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportResultRepository extends JpaRepository<ReportResult, Long> {

    List<ReportResult> findByExecutionIdOrderByRowNumberAsc(Long executionId);

    Page<ReportResult> findByExecutionId(Long executionId, Pageable pageable);

    void deleteByExecutionId(Long executionId);
}
