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
     * Compute AUC (Area Under ROC Curve) via trapezoidal rule over the ROC curve.
     * Requires labeled transactions (label=1 fraud, label=0 legitimate) with model scores.
     * Falls back to the most recently stored ModelMetrics.auc when insufficient labeled
     * data is present in the provided batch.
     */
    private Double computeAUC(List<TransactionFeatures> features) {
        // Filter to labeled records that carry a model score
        List<TransactionFeatures> labeled = features.stream()
            .filter(tf -> tf.getLabel() != null && tf.getScore() != null)
            .collect(Collectors.toList());

        long fraudCount = labeled.stream().filter(tf -> tf.getLabel() == 1).count();
        long goodCount  = labeled.size() - fraudCount;

        if (labeled.size() < 10 || fraudCount == 0 || goodCount == 0) {
            // Not enough labeled data for this batch — use the latest persisted AUC
            return metricsRepository.findFirstByOrderByDateDesc()
                .map(ModelMetrics::getAuc)
                .orElse(0.0);
        }

        // Sort by predicted score descending (highest-risk first)
        labeled.sort((a, b) -> Double.compare(
            b.getScore() != null ? b.getScore() : 0.0,
            a.getScore() != null ? a.getScore() : 0.0));

        // Trapezoidal AUC: walk the ROC curve accumulating area
        double auc = 0.0;
        long tp = 0;
        long fp = 0;
        double prevTpr = 0.0;
        double prevFpr = 0.0;

        for (TransactionFeatures tf : labeled) {
            if (tf.getLabel() == 1) {
                tp++;
            } else {
                fp++;
            }
            double tpr = (double) tp / fraudCount;
            double fpr = (double) fp / goodCount;
            // Trapezoid: width = delta FPR, height = average TPR
            auc += (fpr - prevFpr) * (tpr + prevTpr) / 2.0;
            prevTpr = tpr;
            prevFpr = fpr;
        }

        return Math.min(1.0, Math.max(0.0, auc));
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
     * Compute average scoring latency in milliseconds.
     * Latency per record is (scoredAt - transaction.createdAt).
     * Only records where both timestamps are present and non-negative are included.
     * Returns 0.0 when no usable data is found.
     */
    private Double computeAverageLatency(List<TransactionFeatures> features) {
        double[] latencies = features.stream()
            .filter(tf -> tf.getScoredAt() != null
                && tf.getTransaction() != null
                && tf.getTransaction().getCreatedAt() != null)
            .mapToDouble(tf -> {
                long millis = java.time.Duration.between(
                    tf.getTransaction().getCreatedAt(), tf.getScoredAt()).toMillis();
                return millis;
            })
            .filter(ms -> ms >= 0)
            .toArray();

        if (latencies.length == 0) {
            return 0.0;
        }

        double sum = 0.0;
        for (double ms : latencies) {
            sum += ms;
        }
        return sum / latencies.length;
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

        // Historical AUC average over the last 30 days used as the score baseline.
        // Falls back to 0.5 (neutral midpoint) when no prior metrics exist.
        double baseline = metricsRepository
                .findAverageAucSince(LocalDate.now().minusDays(30))
                .orElse(0.5);
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

