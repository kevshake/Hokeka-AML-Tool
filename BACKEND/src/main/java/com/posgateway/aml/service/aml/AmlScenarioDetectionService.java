package com.posgateway.aml.service.aml;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.compliance.CashStructuringDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AML Scenario Detection Service
 * Detects various money laundering patterns
 */
@Service
public class AmlScenarioDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(AmlScenarioDetectionService.class);

    private final TransactionRepository transactionRepository;
    private final CashStructuringDetectionService cashStructuringDetectionService;

    @Value("${aml.rapid-movement.hours:24}")
    private int rapidMovementHours;

    @Value("${aml.rapid-movement.count:10}")
    private int rapidMovementCount;

    @Value("${aml.funnel-account.min-transactions:5}")
    private int funnelMinTransactions;

    @Value("${aml.funnel-account.time-window-hours:24}")
    private int funnelTimeWindowHours;

    @Value("${aml.funnel-account.minimum-usd:10000}")
    private BigDecimal funnelMinimumUsd;

    @Value("${aml.funnel-account.minimum-pass-through-ratio:0.70}")
    private BigDecimal funnelMinimumPassThroughRatio;

    @Value("${aml.funnel-account.minimum-source-indicators:2}")
    private int funnelMinimumSourceIndicators;

    @Value("${aml.tbml.min-transactions:3}")
    private int tbmlMinTransactions;

    @Value("${aml.tbml.time-window-days:7}")
    private int tbmlTimeWindowDays;

    @Autowired
    public AmlScenarioDetectionService(
            TransactionRepository transactionRepository,
            CashStructuringDetectionService cashStructuringDetectionService) {
        this.transactionRepository = transactionRepository;
        this.cashStructuringDetectionService = cashStructuringDetectionService;
    }

    /**
     * Detect repeated cash transactions immediately below the reporting threshold.
     * Uses approved regulatory FX evidence and rolling 24-hour account windows.
     */
    public List<StructuringDetection> detectStructuring(String merchantId, LocalDateTime startDate, LocalDateTime endDate) {
        List<TransactionEntity> transactions = transactionRepository.findByMerchantIdAndTimestampBetween(
                merchantId, startDate, endDate);
        return cashStructuringDetectionService.detectOccurrences(transactions).stream()
                .map(occurrence -> StructuringDetection.from(merchantId, occurrence))
                .toList();
    }

    /**
     * Detect rapid movement of funds
     */
    public List<RapidMovementDetection> detectRapidMovement(String merchantId, LocalDateTime startDate, LocalDateTime endDate) {
        List<TransactionEntity> transactions = transactionRepository.findByMerchantIdAndTimestampBetween(
                merchantId, startDate, endDate);

        List<RapidMovementDetection> detections = new ArrayList<>();

        // Group transactions by time window
        LocalDateTime currentWindowStart = startDate;
        while (currentWindowStart.isBefore(endDate)) {
            final LocalDateTime windowStart = currentWindowStart;
            LocalDateTime windowEnd = windowStart.plusHours(rapidMovementHours);
            final LocalDateTime finalWindowEnd = windowEnd;
            
            List<TransactionEntity> windowTransactions = transactions.stream()
                    .filter(tx -> tx.getTxnTs() != null && 
                            !tx.getTxnTs().isBefore(windowStart) && 
                            tx.getTxnTs().isBefore(finalWindowEnd))
                    .collect(Collectors.toList());

            if (windowTransactions.size() >= rapidMovementCount) {
                BigDecimal totalAmount = windowTransactions.stream()
                        .map(tx -> tx.getAmountCents() != null ? 
                                BigDecimal.valueOf(tx.getAmountCents()).divide(new BigDecimal("100")) : 
                                BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                detections.add(RapidMovementDetection.builder()
                        .merchantId(merchantId)
                        .windowStart(windowStart)
                        .windowEnd(finalWindowEnd)
                        .transactionCount(windowTransactions.size())
                        .totalAmount(totalAmount)
                        .build());
            }

            currentWindowStart = finalWindowEnd;
        }

        return detections;
    }

    /**
     * Detect round-dollar transactions
     */
    public List<RoundDollarDetection> detectRoundDollar(String merchantId, LocalDateTime startDate, LocalDateTime endDate) {
        List<TransactionEntity> transactions = transactionRepository.findByMerchantIdAndTimestampBetween(
                merchantId, startDate, endDate);

        return transactions.stream()
                .filter(tx -> tx.getAmountCents() != null)
                .map(tx -> {
                    BigDecimal amount = BigDecimal.valueOf(tx.getAmountCents()).divide(new BigDecimal("100"));
                    return isRoundDollar(amount) ? RoundDollarDetection.builder()
                            .transactionId(String.valueOf(tx.getTxnId()))
                            .merchantId(merchantId)
                            .amount(amount)
                            .timestamp(tx.getTxnTs())
                            .build() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * Check if amount is round dollar
     */
    private boolean isRoundDollar(BigDecimal amount) {
        return amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Structuring Detection DTO
     */
    public static class StructuringDetection {
        private String merchantId;
        private String accountId;
        private String date;
        private LocalDateTime windowStart;
        private LocalDateTime windowEnd;
        private String status;
        private int transactionCount;
        /** Total in USD after approved regulatory conversion. */
        private BigDecimal totalAmount;
        /** Regulatory threshold in USD. */
        private BigDecimal threshold;
        private BigDecimal structuringFloorUsd;
        private List<Long> transactionIds;
        private Set<String> currencies;
        private List<Map<String, Object>> conversionEvidence;
        private String unavailableReason;

        public static StructuringDetectionBuilder builder() {
            return new StructuringDetectionBuilder();
        }

        // Getters and Setters
        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public LocalDateTime getWindowStart() { return windowStart; }
        public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
        public LocalDateTime getWindowEnd() { return windowEnd; }
        public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getTransactionCount() { return transactionCount; }
        public void setTransactionCount(int transactionCount) { this.transactionCount = transactionCount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public BigDecimal getThreshold() { return threshold; }
        public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }
        public BigDecimal getStructuringFloorUsd() { return structuringFloorUsd; }
        public void setStructuringFloorUsd(BigDecimal structuringFloorUsd) { this.structuringFloorUsd = structuringFloorUsd; }
        public List<Long> getTransactionIds() { return transactionIds; }
        public void setTransactionIds(List<Long> transactionIds) { this.transactionIds = transactionIds; }
        public Set<String> getCurrencies() { return currencies; }
        public void setCurrencies(Set<String> currencies) { this.currencies = currencies; }
        public List<Map<String, Object>> getConversionEvidence() { return conversionEvidence; }
        public void setConversionEvidence(List<Map<String, Object>> conversionEvidence) { this.conversionEvidence = conversionEvidence; }
        public String getUnavailableReason() { return unavailableReason; }
        public void setUnavailableReason(String unavailableReason) { this.unavailableReason = unavailableReason; }

        static StructuringDetection from(
                String merchantId,
                CashStructuringDetectionService.Occurrence occurrence) {
            StructuringDetection detection = new StructuringDetection();
            detection.merchantId = merchantId;
            detection.accountId = occurrence.accountId();
            detection.date = occurrence.windowStart() != null
                    ? occurrence.windowStart().toLocalDate().toString() : null;
            detection.windowStart = occurrence.windowStart();
            detection.windowEnd = occurrence.windowEnd();
            detection.status = occurrence.status();
            detection.transactionCount = occurrence.transactionCount();
            detection.totalAmount = occurrence.totalUsd();
            detection.threshold = occurrence.thresholdUsd();
            detection.structuringFloorUsd = occurrence.structuringFloorUsd();
            detection.transactionIds = occurrence.transactionIds();
            detection.currencies = occurrence.currencies();
            detection.conversionEvidence = occurrence.conversionEvidence();
            detection.unavailableReason = occurrence.unavailableReason();
            return detection;
        }

        public static class StructuringDetectionBuilder {
            private String merchantId;
            private String date;
            private int transactionCount;
            private BigDecimal totalAmount;
            private BigDecimal threshold;

            public StructuringDetectionBuilder merchantId(String merchantId) {
                this.merchantId = merchantId;
                return this;
            }

            public StructuringDetectionBuilder date(String date) {
                this.date = date;
                return this;
            }

            public StructuringDetectionBuilder transactionCount(int transactionCount) {
                this.transactionCount = transactionCount;
                return this;
            }

            public StructuringDetectionBuilder totalAmount(BigDecimal totalAmount) {
                this.totalAmount = totalAmount;
                return this;
            }

            public StructuringDetectionBuilder threshold(BigDecimal threshold) {
                this.threshold = threshold;
                return this;
            }

            public StructuringDetection build() {
                StructuringDetection detection = new StructuringDetection();
                detection.merchantId = this.merchantId;
                detection.date = this.date;
                detection.transactionCount = this.transactionCount;
                detection.totalAmount = this.totalAmount;
                detection.threshold = this.threshold;
                return detection;
            }
        }
    }

    /**
     * Rapid Movement Detection DTO
     */
    public static class RapidMovementDetection {
        private String merchantId;
        private LocalDateTime windowStart;
        private LocalDateTime windowEnd;
        private int transactionCount;
        private BigDecimal totalAmount;

        public static RapidMovementDetectionBuilder builder() {
            return new RapidMovementDetectionBuilder();
        }

        // Getters and Setters
        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
        public LocalDateTime getWindowStart() { return windowStart; }
        public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
        public LocalDateTime getWindowEnd() { return windowEnd; }
        public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }
        public int getTransactionCount() { return transactionCount; }
        public void setTransactionCount(int transactionCount) { this.transactionCount = transactionCount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

        public static class RapidMovementDetectionBuilder {
            private String merchantId;
            private LocalDateTime windowStart;
            private LocalDateTime windowEnd;
            private int transactionCount;
            private BigDecimal totalAmount;

            public RapidMovementDetectionBuilder merchantId(String merchantId) {
                this.merchantId = merchantId;
                return this;
            }

            public RapidMovementDetectionBuilder windowStart(LocalDateTime windowStart) {
                this.windowStart = windowStart;
                return this;
            }

            public RapidMovementDetectionBuilder windowEnd(LocalDateTime windowEnd) {
                this.windowEnd = windowEnd;
                return this;
            }

            public RapidMovementDetectionBuilder transactionCount(int transactionCount) {
                this.transactionCount = transactionCount;
                return this;
            }

            public RapidMovementDetectionBuilder totalAmount(BigDecimal totalAmount) {
                this.totalAmount = totalAmount;
                return this;
            }

            public RapidMovementDetection build() {
                RapidMovementDetection detection = new RapidMovementDetection();
                detection.merchantId = this.merchantId;
                detection.windowStart = this.windowStart;
                detection.windowEnd = this.windowEnd;
                detection.transactionCount = this.transactionCount;
                detection.totalAmount = this.totalAmount;
                return detection;
            }
        }
    }

    /**
     * Round Dollar Detection DTO
     */
    public static class RoundDollarDetection {
        private String transactionId;
        private String merchantId;
        private BigDecimal amount;
        private LocalDateTime timestamp;

        public static RoundDollarDetectionBuilder builder() {
            return new RoundDollarDetectionBuilder();
        }

        // Getters and Setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public static class RoundDollarDetectionBuilder {
            private String transactionId;
            private String merchantId;
            private BigDecimal amount;
            private LocalDateTime timestamp;

            public RoundDollarDetectionBuilder transactionId(String transactionId) {
                this.transactionId = transactionId;
                return this;
            }

            public RoundDollarDetectionBuilder merchantId(String merchantId) {
                this.merchantId = merchantId;
                return this;
            }

            public RoundDollarDetectionBuilder amount(BigDecimal amount) {
                this.amount = amount;
                return this;
            }

            public RoundDollarDetectionBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public RoundDollarDetection build() {
                RoundDollarDetection detection = new RoundDollarDetection();
                detection.transactionId = this.transactionId;
                detection.merchantId = this.merchantId;
                detection.amount = this.amount;
                detection.timestamp = this.timestamp;
                return detection;
            }
        }
    }

    /**
     * Funnel Account Detection DTO
     */
    public static class FunnelAccountDetection {
        private String merchantId;
        private String accountId;
        private BigDecimal totalReceived;
        private BigDecimal totalTransferred;
        private int transactionCount;
        private int inboundCount;
        private int outboundCount;
        private int distinctSourceIndicators;
        private BigDecimal passThroughRatio;
        private String evidenceCurrency;
        private List<Long> transactionIds;

        // Getters and Setters
        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public BigDecimal getTotalReceived() { return totalReceived; }
        public void setTotalReceived(BigDecimal totalReceived) { this.totalReceived = totalReceived; }
        public BigDecimal getTotalTransferred() { return totalTransferred; }
        public void setTotalTransferred(BigDecimal totalTransferred) { this.totalTransferred = totalTransferred; }
        public int getTransactionCount() { return transactionCount; }
        public void setTransactionCount(int transactionCount) { this.transactionCount = transactionCount; }
        public int getInboundCount() { return inboundCount; }
        public void setInboundCount(int inboundCount) { this.inboundCount = inboundCount; }
        public int getOutboundCount() { return outboundCount; }
        public void setOutboundCount(int outboundCount) { this.outboundCount = outboundCount; }
        public int getDistinctSourceIndicators() { return distinctSourceIndicators; }
        public void setDistinctSourceIndicators(int distinctSourceIndicators) { this.distinctSourceIndicators = distinctSourceIndicators; }
        public BigDecimal getPassThroughRatio() { return passThroughRatio; }
        public void setPassThroughRatio(BigDecimal passThroughRatio) { this.passThroughRatio = passThroughRatio; }
        public String getEvidenceCurrency() { return evidenceCurrency; }
        public void setEvidenceCurrency(String evidenceCurrency) { this.evidenceCurrency = evidenceCurrency; }
        public List<Long> getTransactionIds() { return transactionIds; }
        public void setTransactionIds(List<Long> transactionIds) { this.transactionIds = transactionIds; }
    }

    /**
     * Detect funnel account patterns
     * Funnel accounts receive funds from multiple sources and transfer to single destination
     */
    public List<FunnelAccountDetection> detectFunnelAccounts(String merchantId, LocalDateTime startDate, LocalDateTime endDate) {
        List<TransactionEntity> transactions = transactionRepository.findByMerchantIdAndTimestampBetween(
                merchantId, startDate, endDate);

        // Group transactions by account (using PAN hash as account identifier)
        Map<String, List<TransactionEntity>> accountTransactions = transactions.stream()
                .filter(tx -> tx.getPanHash() != null)
                .collect(Collectors.groupingBy(TransactionEntity::getPanHash));

        List<FunnelAccountDetection> detections = new ArrayList<>();

        for (Map.Entry<String, List<TransactionEntity>> entry : accountTransactions.entrySet()) {
            String accountId = entry.getKey();
            List<TransactionEntity> accountTxs = entry.getValue();

            if (accountTxs.size() < funnelMinTransactions) {
                continue;
            }

            // Calculate totals within time window
            LocalDateTime windowStart = endDate.minusHours(funnelTimeWindowHours);
            List<TransactionEntity> windowTxs = accountTxs.stream()
                    .filter(tx -> tx.getTxnTs() != null && 
                            tx.getTxnTs().isAfter(windowStart) && 
                            tx.getTxnTs().isBefore(endDate))
                    .collect(Collectors.toList());

            if (windowTxs.size() < funnelMinTransactions) {
                continue;
            }

            List<TransactionEntity> inbound = windowTxs.stream()
                    .filter(this::isInbound)
                    .toList();
            List<TransactionEntity> outbound = windowTxs.stream()
                    .filter(this::isOutbound)
                    .toList();
            if (inbound.isEmpty() || outbound.isEmpty()
                    || windowTxs.stream().anyMatch(tx -> tx.getCtrUsdEquivalent() == null)) {
                continue;
            }
            BigDecimal totalReceived = inbound.stream()
                    .map(TransactionEntity::getCtrUsdEquivalent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalTransferred = outbound.stream()
                    .map(TransactionEntity::getCtrUsdEquivalent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal passThroughRatio = totalReceived.signum() == 0
                    ? BigDecimal.ZERO
                    : totalTransferred.divide(totalReceived, 4, java.math.RoundingMode.HALF_UP);
            int sourceIndicators = (int) inbound.stream()
                    .map(this::sourceIndicator)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            if (totalReceived.compareTo(funnelMinimumUsd) >= 0
                    && passThroughRatio.compareTo(funnelMinimumPassThroughRatio) >= 0
                    && sourceIndicators >= funnelMinimumSourceIndicators) {
                FunnelAccountDetection detection = new FunnelAccountDetection();
                detection.setMerchantId(merchantId);
                detection.setAccountId(accountId);
                detection.setTotalReceived(totalReceived);
                detection.setTotalTransferred(totalTransferred);
                detection.setTransactionCount(windowTxs.size());
                detection.setInboundCount(inbound.size());
                detection.setOutboundCount(outbound.size());
                detection.setDistinctSourceIndicators(sourceIndicators);
                detection.setPassThroughRatio(passThroughRatio);
                detection.setEvidenceCurrency("USD");
                detection.setTransactionIds(windowTxs.stream()
                        .map(TransactionEntity::getTxnId)
                        .filter(Objects::nonNull)
                        .toList());
                detections.add(detection);
            }
        }

        logger.info("Detected {} funnel account patterns for merchant {}", detections.size(), merchantId);
        return detections;
    }

    private boolean isInbound(TransactionEntity transaction) {
        if (transaction.getDirection() == null) return false;
        String direction = transaction.getDirection().trim().toUpperCase(Locale.ROOT);
        return direction.startsWith("IN")
                || "CREDIT".equals(direction)
                || "REFUND".equals(direction);
    }

    private boolean isOutbound(TransactionEntity transaction) {
        if (transaction.getDirection() == null) return false;
        String direction = transaction.getDirection().trim().toUpperCase(Locale.ROOT);
        return direction.startsWith("OUT")
                || "DEBIT".equals(direction);
    }

    private String sourceIndicator(TransactionEntity transaction) {
        if (transaction.getDeviceFingerprint() != null
                && !transaction.getDeviceFingerprint().isBlank()) {
            return "DEVICE:" + transaction.getDeviceFingerprint();
        }
        if (transaction.getTerminalId() != null && !transaction.getTerminalId().isBlank()) {
            return "TERMINAL:" + transaction.getTerminalId();
        }
        if (transaction.getChannelType() != null && !transaction.getChannelType().isBlank()) {
            return "CHANNEL:" + transaction.getChannelType();
        }
        return null;
    }

    /**
     * Detect Trade-Based Money Laundering (TBML) patterns
     * TBML involves over/under-invoicing, multiple small transactions, round-trip transactions
     */
    public List<TradeBasedMlDetection> detectTradeBasedMl(String merchantId, LocalDateTime startDate, LocalDateTime endDate) {
        List<TransactionEntity> transactions = transactionRepository.findByMerchantIdAndTimestampBetween(
                merchantId, startDate, endDate);

        List<TradeBasedMlDetection> detections = new ArrayList<>();

        // Group by time windows
        LocalDateTime currentWindowStart = startDate;
        while (currentWindowStart.isBefore(endDate)) {
            final LocalDateTime windowStart = currentWindowStart;
            LocalDateTime windowEnd = windowStart.plusDays(tbmlTimeWindowDays);
            if (windowEnd.isAfter(endDate)) {
                windowEnd = endDate;
            }
            final LocalDateTime finalWindowEnd = windowEnd;

            List<TransactionEntity> windowTxs = transactions.stream()
                    .filter(tx -> tx.getTxnTs() != null &&
                            tx.getTxnTs().isAfter(windowStart) &&
                            tx.getTxnTs().isBefore(finalWindowEnd))
                    .collect(Collectors.toList());

            if (windowTxs.size() >= tbmlMinTransactions) {
                // Check for TBML indicators:
                // 1. Multiple small transactions (structuring)
                // 2. Round-trip transactions (same amount in/out)
                // 3. Unusual transaction patterns

                long smallTxCount = windowTxs.stream()
                        .filter(tx -> tx.getAmountCents() != null && tx.getAmountCents() < 10000) // < $100
                        .count();

                BigDecimal totalAmount = windowTxs.stream()
                        .filter(tx -> tx.getAmountCents() != null)
                        .map(tx -> BigDecimal.valueOf(tx.getAmountCents()).divide(BigDecimal.valueOf(100)))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // TBML indicator: Many small transactions totaling significant amount
                if (smallTxCount >= tbmlMinTransactions && totalAmount.compareTo(BigDecimal.valueOf(50000)) > 0) {
                    TradeBasedMlDetection detection = new TradeBasedMlDetection();
                    detection.setMerchantId(merchantId);
                    detection.setWindowStart(windowStart);
                    detection.setWindowEnd(finalWindowEnd);
                    detection.setTransactionCount(windowTxs.size());
                    detection.setSmallTransactionCount((int) smallTxCount);
                    detection.setTotalAmount(totalAmount);
                    detection.setPattern("MULTIPLE_SMALL_TRANSACTIONS");
                    detections.add(detection);
                }
            }

            currentWindowStart = finalWindowEnd;
        }

        logger.info("Detected {} TBML patterns for merchant {}", detections.size(), merchantId);
        return detections;
    }

    /**
     * Trade-Based Money Laundering Detection DTO
     */
    public static class TradeBasedMlDetection {
        private String merchantId;
        private LocalDateTime windowStart;
        private LocalDateTime windowEnd;
        private int transactionCount;
        private int smallTransactionCount;
        private BigDecimal totalAmount;
        private String pattern; // MULTIPLE_SMALL_TRANSACTIONS, ROUND_TRIP, OVER_INVOICING

        // Getters and Setters
        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
        public LocalDateTime getWindowStart() { return windowStart; }
        public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
        public LocalDateTime getWindowEnd() { return windowEnd; }
        public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }
        public int getTransactionCount() { return transactionCount; }
        public void setTransactionCount(int transactionCount) { this.transactionCount = transactionCount; }
        public int getSmallTransactionCount() { return smallTransactionCount; }
        public void setSmallTransactionCount(int smallTransactionCount) { this.smallTransactionCount = smallTransactionCount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
    }
}

