package com.posgateway.aml.service;

import com.posgateway.aml.entity.ModelMetrics;
import com.posgateway.aml.entity.TransactionFeatures;
import com.posgateway.aml.repository.ModelMetricsRepository;
import com.posgateway.aml.repository.TransactionFeaturesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Monitoring and Metrics Service
 * Tracks model performance metrics: AUC, precision@k, latency, drift
 * Runs scheduled tasks to compute daily metrics
 */
@Service
public class MonitoringMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringMetricsService.class);

    private final ModelMetricsRepository metricsRepository;
    private final TransactionFeaturesRepository featuresRepository;

    @Autowired
    public MonitoringMetricsService(ModelMetricsRepository metricsRepository,
                                   TransactionFeaturesRepository featuresRepository) {
        this.metricsRepository = metricsRepository;
        this.featuresRepository = featuresRepository;
    }

    /**
     * Compute and store daily metrics
     * Scheduled to run daily at midnight
     */
    @Scheduled(cron = "0 0 0 * * ?") // Daily at midnight
    @Transactional
    public void computeDailyMetrics() {
        logger.info("Computing daily model metrics");

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDateTime startOfDay = yesterday.atStartOfDay();
        LocalDateTime endOfDay = yesterday.atTime(23, 59, 59);

        // Get transactions scored yesterday
        List<TransactionFeatures> features = featuresRepository.findScoredInTimeRange(startOfDay, endOfDay);

        if (features.isEmpty()) {
            logger.warn("No transactions found for metrics computation on {}", yesterday);
            return;
        }

        ModelMetrics metrics = new ModelMetrics();
        metrics.setDate(yesterday);

        // Compute AUC (simplified - would need proper ROC curve calculation)
        metrics.setAuc(computeAUC(features));

        // Compute precision@100 (top 100 highest scores)
        metrics.setPrecisionAt100(computePrecisionAtK(features, 100));

        // Compute average latency
        metrics.setAvgLatencyMs(computeAverageLatency(features));

        // Compute drift score (simplified - compares score distribution)
        metrics.setDriftScore(computeDriftScore(features));

        metricsRepository.save(metrics);
        logger.info("Daily metrics computed for {}: AUC={}, Precision@100={}, AvgLatency={}ms, Drift={}",
            yesterday, metrics.getAuc(), metrics.getPrecisionAt100(), 
            metrics.getAvgLatencyMs(), metrics.getDriftScore());
    }

    /**
     * Compute AUC (Area Under ROC Curve)
     * Simplified implementation - would need proper ROC curve calculation
     */
    private Double computeAUC(List<TransactionFeatures> features) {
        // Filter labeled transactions
        List<TransactionFeatures> labeled = features.stream()
            .filter(tf -> tf.getLabel() != null && tf.getScore() != null)
            .collect(Collectors.toList());

        if (labeled.size() < 10) {
            return null; // Not enough data
        }

        // Sort by score descending
        labeled.sort((a, b) -> Double.compare(
            b.getScore() != null ? b.getScore() : 0.0,
            a.getScore() != null ? a.getScore() : 0.0));

        // Count true positives and false positives at each threshold
        long fraudCount = labeled.stream().filter(tf -> tf.getLabel() == 1).count();
        long goodCount = labeled.size() - fraudCount;

        if (fraudCount == 0 || goodCount == 0) {
            return null; // Need both classes
        }

        // Simplified AUC calculation (would use proper ROC curve)
        double auc = 0.5; // Placeholder - implement proper ROC curve calculation
        return auc;
    }

    /**
     * Compute precision at K (top K highest scores)
     */
    private Double computePrecisionAtK(List<TransactionFeatures> features, int k) {
        List<TransactionFeatures> labeled = features.stream()
            .filter(tf -> tf.getLabel() != null && tf.getScore() != null)
            .sorted((a, b) -> Double.compare(
                b.getScore() != null ? b.getScore() : 0.0,
                a.getScore() != null ? a.getScore() : 0.0))
            .limit(k)
            .collect(Collectors.toList());

        if (labeled.isEmpty()) {
            return null;
        }

        long fraudInTopK = labeled.stream().filter(tf -> tf.getLabel() == 1).count();
        return (double) fraudInTopK / labeled.size();
    }

    /**
     * Compute average latency
     */
    private Double computeAverageLatency(List<TransactionFeatures> features) {
        // Latency would be stored separately or computed from timestamps
        // For now, return null as placeholder
        return null;
    }

    /**
     * Compute drift score (simplified - compares score distribution)
     */
    private Double computeDriftScore(List<TransactionFeatures> features) {
        if (features.isEmpty()) {
            return null;
        }

        // Get average score
        double avgScore = features.stream()
            .filter(tf -> tf.getScore() != null)
            .mapToDouble(tf -> tf.getScore())
            .average()
            .orElse(0.0);

        // Compare to baseline (would use historical baseline)
        double baseline = 0.5; // Placeholder baseline
        double drift = Math.abs(avgScore - baseline);

        return drift;
    }

    /**
     * Get latest metrics
     */
    public ModelMetrics getLatestMetrics() {
        return metricsRepository.findFirstByOrderByDateDesc()
            .orElse(null);
    }

    /**
     * Get metrics for a specific date
     */
    public ModelMetrics getMetricsForDate(LocalDate date) {
        return metricsRepository.findByDate(date)
            .orElse(null);
    }

    /**
     * Get metrics for date range
     */
    public List<ModelMetrics> getMetricsForDateRange(LocalDate startDate, LocalDate endDate) {
        return metricsRepository.findAll().stream()
            .filter(m -> !m.getDate().isBefore(startDate) && !m.getDate().isAfter(endDate))
            .collect(Collectors.toList());
    }
}

