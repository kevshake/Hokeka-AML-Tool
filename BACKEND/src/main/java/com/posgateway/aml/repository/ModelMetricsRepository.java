package com.posgateway.aml.repository;

import com.posgateway.aml.entity.ModelMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
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

    /** Metrics within an inclusive date range — avoids a full-table findAll() scan. */
    List<ModelMetrics> findByDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find latest metrics
     */
    Optional<ModelMetrics> findFirstByOrderByDateDesc();

    /**
     * Average AUC over the N most-recent days with a non-null AUC.
     * Used as a historical baseline for drift score computation.
     */
    @Query("SELECT AVG(m.auc) FROM ModelMetrics m WHERE m.date >= :since AND m.auc IS NOT NULL")
    Optional<Double> findAverageAucSince(@Param("since") LocalDate since);
}

