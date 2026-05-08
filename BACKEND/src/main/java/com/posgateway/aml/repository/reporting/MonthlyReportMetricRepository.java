package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.MonthlyReportMetric;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MonthlyReportMetricRepository extends JpaRepository<MonthlyReportMetric, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MonthlyReportMetric m " +
            "WHERE m.yearMonth = :ym " +
            "  AND m.pspId = :pspId " +
            "  AND m.metricName = :metric")
    Optional<MonthlyReportMetric> lockByKey(@Param("ym") String yearMonth,
                                            @Param("pspId") Long pspId,
                                            @Param("metric") String metricName);

    /**
     * Race-safe upsert via Postgres ON CONFLICT. Increments {@code metric_value}
     * by {@code delta} and bumps {@code updated_at}. The unique index on
     * (year_month, psp_id, metric_name) is the conflict target; psp_id is
     * NOT NULL by table definition (V130) so callers MUST resolve a tenant
     * before invoking this method.
     */
    @Modifying
    @Query(value = """
            INSERT INTO monthly_report_metrics (year_month, psp_id, metric_name, metric_value, updated_at)
            VALUES (:ym, :pspId, :metric, :delta, NOW())
            ON CONFLICT (year_month, psp_id, metric_name)
            DO UPDATE SET metric_value = monthly_report_metrics.metric_value + EXCLUDED.metric_value,
                          updated_at   = NOW()
            """, nativeQuery = true)
    int upsertIncrement(@Param("ym") String yearMonth,
                        @Param("pspId") Long pspId,
                        @Param("metric") String metricName,
                        @Param("delta") double delta);
}
