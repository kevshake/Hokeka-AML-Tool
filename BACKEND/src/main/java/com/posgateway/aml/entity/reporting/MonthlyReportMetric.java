package com.posgateway.aml.entity.reporting;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Aggregate row in {@code monthly_report_metrics}.
 *
 * <p>Schema (expected, flagged for migration agent):
 * <pre>
 *   CREATE TABLE monthly_report_metrics (
 *       id            bigserial   PRIMARY KEY,
 *       year_month    char(7)     NOT NULL,         -- e.g. 2026-05
 *       psp_id        bigint,
 *       metric_name   varchar(64) NOT NULL,
 *       metric_value  numeric     NOT NULL DEFAULT 0,
 *       updated_at    timestamp   NOT NULL DEFAULT now(),
 *       UNIQUE (year_month, psp_id, metric_name)
 *   );
 * </pre>
 */
@Entity
@Table(name = "monthly_report_metrics", uniqueConstraints = {
        @UniqueConstraint(name = "uq_monthly_report_metric",
                columnNames = {"year_month", "psp_id", "metric_name"})
})
public class MonthlyReportMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "metric_name", nullable = false, length = 64)
    private String metricName;

    @Column(name = "metric_value", nullable = false)
    private Double metricValue = 0.0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public MonthlyReportMetric() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getYearMonth() { return yearMonth; }
    public void setYearMonth(String yearMonth) { this.yearMonth = yearMonth; }
    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }
    public Double getMetricValue() { return metricValue; }
    public void setMetricValue(Double metricValue) { this.metricValue = metricValue; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
