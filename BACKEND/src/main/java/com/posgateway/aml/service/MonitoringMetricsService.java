package com.posgateway.aml.service;

import com.posgateway.aml.entity.ModelMetrics;
import com.posgateway.aml.entity.TransactionFeatures;
import com.posgateway.aml.repository.ModelMetricsRepository;
import com.posgateway.aml.repository.TransactionFeaturesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes daily model performance metrics from {@code transaction_features}:
 *   - AUC (Mann-Whitney U over labeled rows)
 *   - precision@K (top-K by score, fraction labeled fraud)
 *   - latency: avg + p50 / p95 / p99 from per-txn latency_ms
 *   - drift: PSI of yesterday's score histogram vs a 30-day rolling baseline
 *
 * Heavy aggregations run as Postgres queries (percentile_cont, bucketed counts)
 * so the JVM never holds millions of rows. AUC needs per-row scores so it loads
 * the labeled subset only, which is small.
 */
@Service
public class MonitoringMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringMetricsService.class);

    private static final int    PRECISION_K          = 100;
    private static final int    MIN_LABELED_FOR_AUC  = 10;
    private static final int    PSI_BUCKETS          = 10;
    private static final int    BASELINE_WINDOW_DAYS = 30;
    /** Floor used in PSI to avoid log(0) on empty buckets. */
    private static final double PSI_EPSILON          = 1.0e-4;

    private final ModelMetricsRepository metricsRepository;
    private final TransactionFeaturesRepository featuresRepository;

    public MonitoringMetricsService(ModelMetricsRepository metricsRepository,
                                    TransactionFeaturesRepository featuresRepository) {
        this.metricsRepository = metricsRepository;
        this.featuresRepository = featuresRepository;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void computeDailyMetrics() {
        computeMetricsForDate(LocalDate.now().minusDays(1));
    }

    @Transactional
    public ModelMetrics computeMetricsForDate(LocalDate date) {
        logger.info("Computing model metrics for {}", date);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay   = date.atTime(23, 59, 59, 999_999_999);

        long totalScored = featuresRepository.countScoredInRange(startOfDay, endOfDay);
        if (totalScored == 0) {
            logger.warn("No scored transactions for {} — skipping metrics", date);
            return null;
        }

        ModelMetrics metrics = metricsRepository.findByDate(date).orElseGet(ModelMetrics::new);
        metrics.setDate(date);
        metrics.setTotalScored((int) Math.min(Integer.MAX_VALUE, totalScored));

        // Labeled subset (small) — drives AUC + precision@K + fraud_count.
        List<TransactionFeatures> labeled = loadLabeledSorted(startOfDay, endOfDay);
        metrics.setTotalLabeled(labeled.size());
        metrics.setFraudCount(countFraud(labeled));
        metrics.setAuc(computeAUC(labeled));
        metrics.setPrecisionAt100(computePrecisionAtK(labeled, PRECISION_K));

        metrics.setAvgLatencyMs(featuresRepository.findAverageLatencyInRange(startOfDay, endOfDay));
        Object[] percentiles = featuresRepository.findLatencyPercentilesInRange(startOfDay, endOfDay);
        if (percentiles != null && percentiles.length >= 3) {
            metrics.setP50LatencyMs(toDouble(percentiles[0]));
            metrics.setP95LatencyMs(toDouble(percentiles[1]));
            metrics.setP99LatencyMs(toDouble(percentiles[2]));
        }

        DriftResult drift = computePsiDrift(date);
        metrics.setPsiDrift(drift.psi);
        metrics.setBaselineAvgScore(drift.baselineAvgScore);
        // Keep legacy drift_score populated for dashboards still reading it.
        metrics.setDriftScore(drift.psi);

        metricsRepository.save(metrics);

        logger.info("Metrics for {}: scored={} labeled={} fraud={} auc={} p@100={} avgLat={}ms p95={}ms PSI={}",
                date, metrics.getTotalScored(), metrics.getTotalLabeled(), metrics.getFraudCount(),
                metrics.getAuc(), metrics.getPrecisionAt100(),
                metrics.getAvgLatencyMs(), metrics.getP95LatencyMs(), metrics.getPsiDrift());

        return metrics;
    }

    // ──────────────────────────────────────────────────────────────────────
    // AUC — Mann-Whitney U statistic with average-rank tie handling
    // ──────────────────────────────────────────────────────────────────────

    Double computeAUC(List<TransactionFeatures> labeledSortedDesc) {
        if (labeledSortedDesc.size() < MIN_LABELED_FOR_AUC) return null;

        long nPos = labeledSortedDesc.stream().filter(f -> isFraud(f.getLabel())).count();
        long nNeg = labeledSortedDesc.size() - nPos;
        if (nPos == 0 || nNeg == 0) return null;

        List<TransactionFeatures> asc = new ArrayList<>(labeledSortedDesc);
        asc.sort(Comparator.comparingDouble(f -> nullSafe(f.getScore())));

        double rankSumPositives = 0.0;
        int i = 0;
        int n = asc.size();
        while (i < n) {
            int j = i;
            double currentScore = nullSafe(asc.get(i).getScore());
            while (j + 1 < n && nullSafe(asc.get(j + 1).getScore()) == currentScore) j++;
            double avgRank = ((i + 1) + (j + 1)) / 2.0;
            for (int k = i; k <= j; k++) {
                if (isFraud(asc.get(k).getLabel())) rankSumPositives += avgRank;
            }
            i = j + 1;
        }

        double auc = (rankSumPositives - nPos * (nPos + 1) / 2.0) / ((double) nPos * nNeg);
        return clamp01(auc);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Precision@K — denominator is K; null when fewer than K labeled rows
    // ──────────────────────────────────────────────────────────────────────

    Double computePrecisionAtK(List<TransactionFeatures> labeledSortedDesc, int k) {
        if (k <= 0 || labeledSortedDesc.size() < k) return null;
        long fraudInTopK = labeledSortedDesc.stream()
                .limit(k)
                .filter(f -> isFraud(f.getLabel()))
                .count();
        return (double) fraudInTopK / k;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Drift — Population Stability Index
    // PSI < 0.10 stable, 0.10-0.25 minor shift, > 0.25 major shift
    // ──────────────────────────────────────────────────────────────────────

    DriftResult computePsiDrift(LocalDate date) {
        LocalDateTime targetStart = date.atStartOfDay();
        LocalDateTime targetEnd   = date.atTime(23, 59, 59, 999_999_999);
        LocalDateTime baseEnd     = date.atStartOfDay().minusNanos(1);
        LocalDateTime baseStart   = date.minusDays(BASELINE_WINDOW_DAYS).atStartOfDay();

        double[] target   = bucketize(featuresRepository.findScoreHistogramInRange(targetStart, targetEnd));
        double[] baseline = bucketize(featuresRepository.findScoreHistogramInRange(baseStart, baseEnd));

        Double baselineAvg = featuresRepository.findAverageScoreInRange(baseStart, baseEnd);

        double targetTotal   = sum(target);
        double baselineTotal = sum(baseline);
        if (targetTotal == 0 || baselineTotal == 0) {
            return new DriftResult(null, baselineAvg);
        }

        double psi = 0.0;
        for (int b = 0; b < PSI_BUCKETS; b++) {
            double actual   = Math.max(target[b]   / targetTotal,   PSI_EPSILON);
            double expected = Math.max(baseline[b] / baselineTotal, PSI_EPSILON);
            psi += (actual - expected) * Math.log(actual / expected);
        }
        return new DriftResult(psi, baselineAvg);
    }

    private double[] bucketize(List<Object[]> rows) {
        double[] buckets = new double[PSI_BUCKETS];
        if (rows == null) return buckets;
        for (Object[] row : rows) {
            int idx    = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            if (idx >= 0 && idx < PSI_BUCKETS) buckets[idx] = count;
        }
        return buckets;
    }

    private double sum(double[] a) {
        double s = 0;
        for (double v : a) s += v;
        return s;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers / read accessors
    // ──────────────────────────────────────────────────────────────────────

    private List<TransactionFeatures> loadLabeledSorted(LocalDateTime start, LocalDateTime end) {
        List<TransactionFeatures> rows = featuresRepository.findScoredInTimeRange(start, end);
        rows.removeIf(r -> r.getLabel() == null || r.getScore() == null);
        rows.sort(Comparator.comparingDouble((TransactionFeatures f) -> nullSafe(f.getScore())).reversed());
        return rows;
    }

    private static int countFraud(List<TransactionFeatures> labeled) {
        return (int) labeled.stream().filter(f -> isFraud(f.getLabel())).count();
    }

    private static boolean isFraud(Short label)   { return label != null && label == 1; }
    private static double  nullSafe(Double d)     { return d == null ? 0.0 : d; }
    private static double  clamp01(double v)      { return v < 0 ? 0.0 : (v > 1 ? 1.0 : v); }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    public ModelMetrics getLatestMetrics() {
        return metricsRepository.findFirstByOrderByDateDesc().orElse(null);
    }

    public ModelMetrics getMetricsForDate(LocalDate date) {
        return metricsRepository.findByDate(date).orElse(null);
    }

    public List<ModelMetrics> getMetricsForDateRange(LocalDate startDate, LocalDate endDate) {
        return metricsRepository.findAll().stream()
                .filter(m -> m.getDate() != null
                        && !m.getDate().isBefore(startDate)
                        && !m.getDate().isAfter(endDate))
                .sorted(Comparator.comparing(ModelMetrics::getDate))
                .toList();
    }

    static final class DriftResult {
        final Double psi;
        final Double baselineAvgScore;
        DriftResult(Double psi, Double baselineAvgScore) {
            this.psi = psi;
            this.baselineAvgScore = baselineAvgScore;
        }
    }
}
