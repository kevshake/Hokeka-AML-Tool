package com.posgateway.aml.service.analytics;



import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// @RequiredArgsConstructor removed
@Service
public class BehavioralProfilingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BehavioralProfilingService.class);

    private final TransactionRepository transactionRepository;

    public BehavioralProfilingService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    private static final int HISTORY_DAYS = 90;
    /** Minimum historical sample size before a z-score is statistically meaningful. */
    private static final int MIN_SAMPLE = 10;
    /** Cap on history rows pulled for the baseline (bounds latency/memory). */
    private static final int MAX_SAMPLE = 1000;
    /** Flag amounts at or beyond this many standard deviations from the mean. */
    private static final double EXTREME_OUTLIER_SIGMA = 3.0;

    /**
     * Checks whether the transaction amount is a statistical outlier for this
     * merchant's recent history using a real z-score: {@code (value - mean) / stddev}.
     *
     * <p>The baseline is the merchant's amounts over the last {@link #HISTORY_DAYS}
     * days (up to {@link #MAX_SAMPLE} most-recent rows). An amount is anomalous when its
     * z-score is at least {@link #EXTREME_OUTLIER_SIGMA}. When the historical amounts are
     * all identical (stddev = 0) any strictly larger amount is treated as an outlier.
     *
     * @param transaction current transaction
     * @return true if the amount is a significant high-side anomaly
     */
    public boolean isAmountAnomaly(Transaction transaction) {
        if (transaction.getAmount() == null)
            return false;

        String merchantId = transaction.getMerchantId();
        // Anchor the baseline window to the transaction's own event time (not wall-clock
        // now), and make the upper bound exclusive so the current transaction — and any
        // later ones — cannot dilute its own mean/stddev and damp the z-score. Falls back
        // to now() only when the event timestamp is absent.
        LocalDateTime txnTime = transaction.getTransactionTimestamp() != null
                ? transaction.getTransactionTimestamp() : LocalDateTime.now();
        LocalDateTime endTime = txnTime.minusNanos(1);
        LocalDateTime startTime = endTime.minusDays(HISTORY_DAYS);

        // Pull the merchant's recent amounts (cents) and compute a real mean + population
        // standard deviation. This replaces the previous mean×5 heuristic.
        List<Long> amountsCents = transactionRepository.findAmountsByMerchantInTimeWindow(
                merchantId, startTime, endTime, PageRequest.of(0, MAX_SAMPLE));
        if (amountsCents == null || amountsCents.size() < MIN_SAMPLE) {
            // Not enough history to profile.
            return false;
        }

        int n = amountsCents.size();
        double mean = 0.0;
        for (Long c : amountsCents) {
            mean += (c == null ? 0L : c);
        }
        mean /= n;

        double variance = 0.0;
        for (Long c : amountsCents) {
            double d = (c == null ? 0L : c) - mean;
            variance += d * d;
        }
        variance /= n; // population variance
        double stdDev = Math.sqrt(variance);

        double currentCents = transaction.getAmount().doubleValue() * 100.0;

        if (stdDev == 0.0) {
            // All historical amounts identical: any strictly larger amount is anomalous.
            boolean anomaly = currentCents > mean;
            if (anomaly) {
                log.warn("Behavioral anomaly: amount {} exceeds constant merchant baseline {} (merchant {})",
                        currentCents / 100.0, mean / 100.0, merchantId);
            }
            return anomaly;
        }

        double zScore = (currentCents - mean) / stdDev;
        if (zScore >= EXTREME_OUTLIER_SIGMA) {
            log.warn("Behavioral anomaly: amount {} has z-score {} (>= {}σ) vs merchant {} baseline (mean {}, sd {})",
                    currentCents / 100.0, String.format("%.2f", zScore), EXTREME_OUTLIER_SIGMA, merchantId,
                    mean / 100.0, stdDev / 100.0);
            return true;
        }

        return false;
    }
}
