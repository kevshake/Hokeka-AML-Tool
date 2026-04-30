package com.posgateway.aml.repository;

import com.posgateway.aml.entity.ModelMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository for Model Metrics
 */
@Repository
public interface ModelMetricsRepository extends JpaRepository<ModelMetrics, Long> {

    /**
     * Find metrics by date
     */
    Optional<ModelMetrics> findByDate(LocalDate date);

    /**
     * Find latest metrics
     */
    Optional<ModelMetrics> findFirstByOrderByDateDesc();
}

