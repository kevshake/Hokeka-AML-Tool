package com.posgateway.aml.service.analytics;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Behavioral Analytics Service
 * Provides peer group comparison and dormant account detection
 */
@Service
public class BehavioralAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(BehavioralAnalyticsService.class);

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;

    @Autowired
    public BehavioralAnalyticsService(
            TransactionRepository transactionRepository,
            MerchantRepository merchantRepository) {
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
    }

    /**
     * Compare merchant behavior to peer group
     */
    public PeerGroupComparison compareToPeerGroup(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found: " + merchantId));

        // Get peer group (same MCC, similar size)
        List<Merchant> peerGroup = getPeerGroup(merchant);

        // Calculate merchant metrics
        Map<String, Object> merchantMetrics = calculateMerchantMetrics(merchantId);

        // Calculate peer group metrics
        Map<String, Object> peerMetrics = calculatePeerGroupMetrics(peerGroup);

        PeerGroupComparison comparison = new PeerGroupComparison();
        comparison.setMerchantId(merchantId);
        comparison.setMerchantName(merchant.getLegalName());
        comparison.setMerchantMetrics(merchantMetrics);
        comparison.setPeerGroupMetrics(peerMetrics);
        comparison.setDeviations(identifyDeviations(merchantMetrics, peerMetrics));

        return comparison;
    }

    /**
     * Detect dormant account reactivation
     */
    public List<DormantAccountReactivation> detectDormantAccountReactivation(int dormantDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(dormantDays);
        LocalDateTime recentDate = LocalDateTime.now().minusDays(7);

        // Get all merchants
        List<Merchant> merchants = merchantRepository.findAll();
        List<DormantAccountReactivation> detections = new ArrayList<>();

        for (Merchant merchant : merchants) {
            // Get last transaction before cutoff
            List<TransactionEntity> oldTransactions = transactionRepository
                    .findByMerchantIdAndTimestampBetween(
                            merchant.getMerchantId().toString(),
                            LocalDateTime.now().minusYears(2),
                            cutoffDate);

            // Get recent transactions
            List<TransactionEntity> recentTransactions = transactionRepository
                    .findByMerchantIdAndTimestampBetween(
                            merchant.getMerchantId().toString(),
                            recentDate,
                            LocalDateTime.now());

            // If had old transactions but no recent ones, then reactivated
            if (!oldTransactions.isEmpty() && !recentTransactions.isEmpty()) {
                // Check if there was a gap
                LocalDateTime lastOldTx = oldTransactions.stream()
                        .map(TransactionEntity::getTxnTs)
                        .max(LocalDateTime::compareTo)
                        .orElse(null);

                LocalDateTime firstRecentTx = recentTransactions.stream()
                        .map(TransactionEntity::getTxnTs)
                        .min(LocalDateTime::compareTo)
                        .orElse(null);

                if (lastOldTx != null && firstRecentTx != null) {
                    long daysGap = java.time.temporal.ChronoUnit.DAYS.between(lastOldTx, firstRecentTx);
                    if (daysGap >= dormantDays) {
                        DormantAccountReactivation detection = new DormantAccountReactivation();
                        detection.setMerchantId(merchant.getMerchantId());
                        detection.setMerchantName(merchant.getLegalName());
                        detection.setLastTransactionBeforeDormant(lastOldTx);
                        detection.setFirstTransactionAfterReactivation(firstRecentTx);
                        detection.setDaysDormant((int) daysGap);
                        detection.setRecentTransactionCount(recentTransactions.size());
                        detections.add(detection);
                    }
                }
            }
        }

        logger.info("Detected {} dormant account reactivations", detections.size());
        return detections;
    }

    /**
     * Get peer group for merchant
     */
    private List<Merchant> getPeerGroup(Merchant merchant) {
        // Peer group: same MCC code
        return merchantRepository.findAll().stream()
                .filter(m -> merchant.getMcc().equals(m.getMcc()))
                .filter(m -> !m.getMerchantId().equals(merchant.getMerchantId()))
                .limit(20) // Top 20 peers
                .toList();
    }

    /**
     * Calculate merchant metrics
     */
    private Map<String, Object> calculateMerchantMetrics(Long merchantId) {
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        List<TransactionEntity> transactions = transactionRepository
                .findByMerchantIdAndTimestampBetween(
                        merchantId.toString(),
                        last30Days,
                        LocalDateTime.now());

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("transactionCount", transactions.size());
        
        BigDecimal totalAmount = transactions.stream()
                .filter(tx -> tx.getAmountCents() != null)
                .map(tx -> BigDecimal.valueOf(tx.getAmountCents()).divide(BigDecimal.valueOf(100)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        metrics.put("totalAmount", totalAmount);
        metrics.put("averageAmount", transactions.isEmpty() ? BigDecimal.ZERO : 
                totalAmount.divide(BigDecimal.valueOf(transactions.size()), 2, java.math.RoundingMode.HALF_UP));

        return metrics;
    }

    /**
     * Calculate peer group metrics
     */
    private Map<String, Object> calculatePeerGroupMetrics(List<Merchant> peerGroup) {
        Map<String, Object> metrics = new HashMap<>();
        int totalTransactions = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Merchant peer : peerGroup) {
            Map<String, Object> peerMetrics = calculateMerchantMetrics(peer.getMerchantId());
            totalTransactions += (Integer) peerMetrics.get("transactionCount");
            totalAmount = totalAmount.add((BigDecimal) peerMetrics.get("totalAmount"));
        }

        int peerCount = peerGroup.size();
        metrics.put("averageTransactionCount", peerCount > 0 ? totalTransactions / peerCount : 0);
        metrics.put("averageTotalAmount", peerCount > 0 ? 
                totalAmount.divide(BigDecimal.valueOf(peerCount), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);

        return metrics;
    }

    /**
     * Identify deviations from peer group
     */
    private List<String> identifyDeviations(Map<String, Object> merchantMetrics, Map<String, Object> peerMetrics) {
        List<String> deviations = new ArrayList<>();

        int merchantTxCount = (Integer) merchantMetrics.get("transactionCount");
        int peerAvgTxCount = (Integer) peerMetrics.get("averageTransactionCount");
        
        if (merchantTxCount > peerAvgTxCount * 2) {
            deviations.add("Transaction count significantly higher than peer group");
        } else if (merchantTxCount < peerAvgTxCount / 2) {
            deviations.add("Transaction count significantly lower than peer group");
        }

        BigDecimal merchantAmount = (BigDecimal) merchantMetrics.get("totalAmount");
        BigDecimal peerAvgAmount = (BigDecimal) peerMetrics.get("averageTotalAmount");
        
        if (peerAvgAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = merchantAmount.divide(peerAvgAmount, 2, java.math.RoundingMode.HALF_UP);
            if (ratio.compareTo(BigDecimal.valueOf(2)) > 0) {
                deviations.add("Transaction volume significantly higher than peer group");
            } else if (ratio.compareTo(BigDecimal.valueOf(0.5)) < 0) {
                deviations.add("Transaction volume significantly lower than peer group");
            }
        }

        return deviations;
    }

    /**
     * Peer Group Comparison DTO
     */
    public static class PeerGroupComparison {
        private Long merchantId;
        private String merchantName;
        private Map<String, Object> merchantMetrics;
        private Map<String, Object> peerGroupMetrics;
        private List<String> deviations;

        // Getters and Setters
        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        public Map<String, Object> getMerchantMetrics() { return merchantMetrics; }
        public void setMerchantMetrics(Map<String, Object> merchantMetrics) { this.merchantMetrics = merchantMetrics; }
        public Map<String, Object> getPeerGroupMetrics() { return peerGroupMetrics; }
        public void setPeerGroupMetrics(Map<String, Object> peerGroupMetrics) { this.peerGroupMetrics = peerGroupMetrics; }
        public List<String> getDeviations() { return deviations; }
        public void setDeviations(List<String> deviations) { this.deviations = deviations; }
    }

    /**
     * Dormant Account Reactivation DTO
     */
    public static class DormantAccountReactivation {
        private Long merchantId;
        private String merchantName;
        private LocalDateTime lastTransactionBeforeDormant;
        private LocalDateTime firstTransactionAfterReactivation;
        private int daysDormant;
        private int recentTransactionCount;

        // Getters and Setters
        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        public LocalDateTime getLastTransactionBeforeDormant() { return lastTransactionBeforeDormant; }
        public void setLastTransactionBeforeDormant(LocalDateTime lastTransactionBeforeDormant) { this.lastTransactionBeforeDormant = lastTransactionBeforeDormant; }
        public LocalDateTime getFirstTransactionAfterReactivation() { return firstTransactionAfterReactivation; }
        public void setFirstTransactionAfterReactivation(LocalDateTime firstTransactionAfterReactivation) { this.firstTransactionAfterReactivation = firstTransactionAfterReactivation; }
        public int getDaysDormant() { return daysDormant; }
        public void setDaysDormant(int daysDormant) { this.daysDormant = daysDormant; }
        public int getRecentTransactionCount() { return recentTransactionCount; }
        public void setRecentTransactionCount(int recentTransactionCount) { this.recentTransactionCount = recentTransactionCount; }
    }
}

