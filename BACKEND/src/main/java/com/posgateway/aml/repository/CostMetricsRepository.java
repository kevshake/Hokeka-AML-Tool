package com.posgateway.aml.repository;

import com.posgateway.aml.entity.billing.CostMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface CostMetricsRepository extends JpaRepository<CostMetrics, Long> {

    Optional<CostMetrics> findByMetricDate(LocalDate metricDate);

    @Query("SELECT cm FROM CostMetrics cm ORDER BY cm.metricDate DESC LIMIT 1")
    Optional<CostMetrics> findLatest();

    @Query("SELECT cm FROM CostMetrics cm WHERE cm.metricDate <= :date ORDER BY cm.metricDate DESC LIMIT 1")
    Optional<CostMetrics> findLatestAsOf(LocalDate date);
}
