package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.model.AlertDisposition;
import com.posgateway.aml.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Alert Disposition Service
 * Manages alert disposition workflow
 */
@Service
public class AlertDispositionService {

    private static final Logger logger = LoggerFactory.getLogger(AlertDispositionService.class);

    private final AlertRepository alertRepository;

    @Autowired
    public AlertDispositionService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Dispose an alert
     */
    @Transactional
    public Alert disposeAlert(Long alertId, AlertDisposition disposition, String reason, User user) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        alert.setDisposition(disposition);
        alert.setDispositionReason(reason);
        alert.setDisposedBy(user.getUsername());
        alert.setDisposedAt(LocalDateTime.now());
        alert.setStatus("closed");

        logger.info("Alert {} disposed as {} by {}", alertId, disposition, user.getUsername());
        return alertRepository.save(alert);
    }

    /**
     * Get alert disposition statistics
     */
    public AlertDispositionStats getDispositionStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        List<Alert> alerts = alertRepository.findAlertsInTimeRange(startDate, endDate);

        long total = alerts.size();
        long falsePositives = alerts.stream()
                .filter(a -> a.getDisposition() != null && a.getDisposition().isFalsePositive())
                .count();
        // Count all true positives using helper on enum
        long truePositives = alerts.stream()
                .filter(a -> a.getDisposition() != null && a.getDisposition().isTruePositive())
                .count();
        // Specifically count SAR-filed true positives
        long sarFiled = alerts.stream()
                .filter(a -> a.getDisposition() == AlertDisposition.TRUE_POSITIVE_SAR_FILED)
                .count();
        long escalated = alerts.stream()
                .filter(a -> a.getDisposition() == AlertDisposition.ESCALATED)
                .count();

        return AlertDispositionStats.builder()
                .totalAlerts(total)
                .falsePositives(falsePositives)
                .truePositives(truePositives)
                .sarFiled(sarFiled)
                .escalated(escalated)
                .falsePositiveRate(total > 0 ? (double) falsePositives / total * 100 : 0.0)
                .build();
    }

    /**
     * Get disposition distribution
     */
    public Map<AlertDisposition, Long> getDispositionDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        List<Alert> alerts = alertRepository.findAlertsInTimeRange(startDate, endDate);
        return alerts.stream()
                .filter(a -> a.getDisposition() != null)
                .collect(Collectors.groupingBy(Alert::getDisposition, Collectors.counting()));
    }

    /**
     * Alert Disposition Statistics DTO
     */
    public static class AlertDispositionStats {
        private long totalAlerts;
        private long falsePositives;
        private long truePositives;
        private long sarFiled;
        private long escalated;
        private double falsePositiveRate;

        public static AlertDispositionStatsBuilder builder() {
            return new AlertDispositionStatsBuilder();
        }

        // Getters and Setters
        public long getTotalAlerts() { return totalAlerts; }
        public void setTotalAlerts(long totalAlerts) { this.totalAlerts = totalAlerts; }
        public long getFalsePositives() { return falsePositives; }
        public void setFalsePositives(long falsePositives) { this.falsePositives = falsePositives; }
        public long getTruePositives() { return truePositives; }
        public void setTruePositives(long truePositives) { this.truePositives = truePositives; }
        public long getSarFiled() { return sarFiled; }
        public void setSarFiled(long sarFiled) { this.sarFiled = sarFiled; }
        public long getEscalated() { return escalated; }
        public void setEscalated(long escalated) { this.escalated = escalated; }
        public double getFalsePositiveRate() { return falsePositiveRate; }
        public void setFalsePositiveRate(double falsePositiveRate) { this.falsePositiveRate = falsePositiveRate; }

        public static class AlertDispositionStatsBuilder {
            private long totalAlerts;
            private long falsePositives;
            private long truePositives;
            private long sarFiled;
            private long escalated;
            private double falsePositiveRate;

            public AlertDispositionStatsBuilder totalAlerts(long totalAlerts) {
                this.totalAlerts = totalAlerts;
                return this;
            }

            public AlertDispositionStatsBuilder falsePositives(long falsePositives) {
                this.falsePositives = falsePositives;
                return this;
            }

            public AlertDispositionStatsBuilder truePositives(long truePositives) {
                this.truePositives = truePositives;
                return this;
            }

            public AlertDispositionStatsBuilder sarFiled(long sarFiled) {
                this.sarFiled = sarFiled;
                return this;
            }

            public AlertDispositionStatsBuilder escalated(long escalated) {
                this.escalated = escalated;
                return this;
            }

            public AlertDispositionStatsBuilder falsePositiveRate(double falsePositiveRate) {
                this.falsePositiveRate = falsePositiveRate;
                return this;
            }

            public AlertDispositionStats build() {
                AlertDispositionStats stats = new AlertDispositionStats();
                stats.totalAlerts = this.totalAlerts;
                stats.falsePositives = this.falsePositives;
                stats.truePositives = this.truePositives;
                stats.sarFiled = this.sarFiled;
                stats.escalated = this.escalated;
                stats.falsePositiveRate = this.falsePositiveRate;
                return stats;
            }
        }
    }
}

