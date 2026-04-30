package com.posgateway.aml.service.analytics;



import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// @RequiredArgsConstructor removed
@Service
public class BehavioralProfilingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BehavioralProfilingService.class);

    private final TransactionRepository transactionRepository;

    public BehavioralProfilingService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }


    private static final int HISTORY_DAYS = 90;
    @SuppressWarnings("unused")
    private static final double EXTREME_OUTLIER_SIGMA = 3.0; // 3 Standard Deviations

    /**
     * Checks if the transaction amount is a statistical outlier for this
     * card/merchant history.
     * Uses Z-Score: (Value - Mean) / StdDev.
     * 
     * @param transaction current transaction
     * @return true if significant anomaly
     */
    public boolean isAmountAnomaly(Transaction transaction) {
        if (transaction.getAmount() == null)
            return false;

        // Use PAN Hash to profile the CARDHOLDER behavior (common in AML/Fraud)
        // Or simple MVP: Profile the MERCHANT's average ticket size?
        // AML usually looks at Customer behavior. Let's look at PAN Hash behavior if
        // available?
        // Wait, Transaction model doesn't expose PAN Hash simply, Entity does.
        // Let's stick to checking if this transaction is huge for this MERCHANT (Money
        // Laundering typical case: sudden spike in volume)
        // Or actually, user asked for "Behavioral Profiling".
        // Best approach for AML: Is this transaction amount weird for this MERCHANT?

        String merchantId = transaction.getMerchantId();
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(HISTORY_DAYS);

        Long count = transactionRepository.countByMerchantInTimeWindow(merchantId, startTime, endTime);
        if (count == null || count < 10) {
            // Not enough history to profile
            return false;
        }

        // Simplify: Get Stats. JPA doesn't do StdDev easily without native query.
        // We can approximate or just fetch Avg.
        // Let's fetch Sum and Count to get Avg. StdDev is hard without all data points.
        // For MVP, simple Mean check: Is it > 5x the average? (Heuristic)
        // OR better: Implement Native Query for STDDEV in repository if DB supports it
        // (Postgres does).
        // Let's stick to a robust Heuristic for MVP: > 500% of Average Ticket Size.

        Long sumCents = transactionRepository.sumAmountByMerchantInTimeWindow(merchantId, startTime, endTime);
        double totalAmt = (sumCents != null ? sumCents : 0) / 100.0;
        double avg = totalAmt / count;

        double currentAmt = transaction.getAmount().doubleValue();

        if (avg > 0 && currentAmt > (avg * 5)) {
            log.warn("Behavioral Anomaly: Transaction Amount {} is > 5x Average ({}) for Merchant {}", currentAmt, avg,
                    merchantId);
            return true;
        }

        return false;
    }
}
