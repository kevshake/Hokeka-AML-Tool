package com.posgateway.aml.repository;

import com.posgateway.aml.entity.TransactionFeatures;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Transaction Features
 */
@Repository
public interface TransactionFeaturesRepository extends JpaRepository<TransactionFeatures, Long> {

    /**
     * Find features by transaction ID
     */
    TransactionFeatures findByTxnId(Long txnId);

    /**
     * Find labeled transactions for training
     */
    @Query("SELECT tf FROM TransactionFeatures tf WHERE tf.label IS NOT NULL")
    List<TransactionFeatures> findLabeledTransactions();

    /**
     * Find transactions scored in time range
     */
    @Query("SELECT tf FROM TransactionFeatures tf WHERE tf.scoredAt >= :startTime AND tf.scoredAt <= :endTime")
    List<TransactionFeatures> findScoredInTimeRange(@Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime);

    /**
     * Latency percentiles for a window. Computed in Postgres so we don't pull
     * potentially millions of rows into the JVM. Returns one row of three doubles:
     * p50, p95, p99 (each in ms). Rows with NULL latency_ms are ignored.
     */
    @Query(value = "SELECT " +
            "  percentile_cont(0.50) WITHIN GROUP (ORDER BY latency_ms) AS p50, " +
            "  percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95, " +
            "  percentile_cont(0.99) WITHIN GROUP (ORDER BY latency_ms) AS p99 " +
            "FROM transaction_features " +
            "WHERE scored_at >= :startTime AND scored_at <= :endTime " +
            "  AND latency_ms IS NOT NULL", nativeQuery = true)
    Object[] findLatencyPercentilesInRange(@Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * Average latency in a window (NULL-safe; AVG ignores NULLs).
     */
    @Query(value = "SELECT AVG(latency_ms) FROM transaction_features " +
            "WHERE scored_at >= :startTime AND scored_at <= :endTime " +
            "  AND latency_ms IS NOT NULL", nativeQuery = true)
    Double findAverageLatencyInRange(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * Score histogram bucketed into 10 deciles [0.0,0.1)..[0.9,1.0]. Used by
     * MonitoringMetricsService for PSI (Population Stability Index) drift.
     * Returns list of [bucket_index INT, count BIGINT] rows.
     */
    @Query(value = "SELECT bucket, COUNT(*) AS cnt FROM ( " +
            "  SELECT LEAST(9, CAST(FLOOR(GREATEST(0.0, LEAST(1.0, score)) * 10) AS int)) AS bucket " +
            "  FROM transaction_features " +
            "  WHERE scored_at >= :startTime AND scored_at <= :endTime " +
            "    AND score IS NOT NULL " +
            ") t GROUP BY bucket ORDER BY bucket", nativeQuery = true)
    List<Object[]> findScoreHistogramInRange(@Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * Count rows scored in a window (used to size histograms / report totals).
     */
    @Query(value = "SELECT COUNT(*) FROM transaction_features " +
            "WHERE scored_at >= :startTime AND scored_at <= :endTime " +
            "  AND score IS NOT NULL", nativeQuery = true)
    long countScoredInRange(@Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);

    /**
     * Average score in a window (uses Postgres AVG for efficiency).
     */
    @Query(value = "SELECT AVG(score) FROM transaction_features " +
            "WHERE scored_at >= :startTime AND scored_at <= :endTime " +
            "  AND score IS NOT NULL", nativeQuery = true)
    Double findAverageScoreInRange(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);
}
