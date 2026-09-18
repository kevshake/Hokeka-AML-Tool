package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Optimized Feature Extraction Service
 * High-throughput version with caching and parallel processing
 */
@Service
public class OptimizedFeatureExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(OptimizedFeatureExtractionService.class);

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final com.posgateway.aml.service.enrichment.IpReputationService ipReputationService;

    @Value("${throughput.parallel.feature.extraction:true}")
    private boolean parallelEnabled;

    @Value("${aml.features.high-value-amount-cents:1000000}")
    private long highValueAmountCents = 1_000_000L;

    @Autowired
    public OptimizedFeatureExtractionService(TransactionRepository transactionRepository,
                                           ObjectMapper objectMapper,
                                           com.posgateway.aml.service.enrichment.IpReputationService ipReputationService) {
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
        this.ipReputationService = ipReputationService;
    }

    /**
     * IP-reputation features (VPN/proxy/datacenter + manipulated source). Deterministic, no network
     * call — safe for the ultra-high-throughput hot path. Mirrors FeatureExtractionService so both
     * the ultra and standard pipelines feed the same signals to scoring and the dynamic rules.
     */
    private void extractIpReputationFeatures(TransactionEntity transaction, Map<String, Object> features) {
        var assessment = ipReputationService.assess(transaction.getIpAddress(), null, null);
        features.put("ip_present", assessment.present());
        features.put("ip_vpn_or_proxy", assessment.anonymizing());
        features.put("ip_manipulated", assessment.malformed() || assessment.privateOrReserved());
        features.put("ip_reputation", assessment.category());
    }

    /**
     * Extract features with caching and parallel processing
     * Optimized for high concurrency with minimal object allocation
     */
    public Map<String, Object> extractFeatures(TransactionEntity transaction) {
        // Pre-allocate HashMap with estimated capacity (64) to avoid resizing
        Map<String, Object> features = new HashMap<>(64);

        // Extract transaction-level features (fast, no I/O) - ~1ms
        extractTransactionFeatures(transaction, features);

        // Extract behavioral features in parallel if enabled - ~5-10ms
        if (parallelEnabled) {
            extractBehavioralFeaturesParallel(transaction, features);
        } else {
            extractBehavioralFeatures(transaction, features);
        }

        // Extract EMV features (fast, no I/O) - ~1ms
        extractEmvFeatures(transaction, features);

        // Extract AML features - ~2-5ms
        extractAmlFeatures(transaction, features);

        // IP reputation (VPN/proxy/manipulated) — deterministic, no I/O
        extractIpReputationFeatures(transaction, features);

        return features;
    }

    /**
     * Extract features with minimal allocation (for ultra-high throughput)
     * Reuses provided map to avoid allocation
     */
    public void extractFeaturesInto(TransactionEntity transaction, Map<String, Object> features) {
        extractTransactionFeatures(transaction, features);
        
        if (parallelEnabled) {
            extractBehavioralFeaturesParallel(transaction, features);
        } else {
            extractBehavioralFeatures(transaction, features);
        }
        
        extractEmvFeatures(transaction, features);
        extractAmlFeatures(transaction, features);
        extractIpReputationFeatures(transaction, features);
    }

    /**
     * Extract behavioral features in parallel
     */
    private void extractBehavioralFeaturesParallel(TransactionEntity transaction, Map<String, Object> features) {
        LocalDateTime now = transaction.getTxnTs() != null ? transaction.getTxnTs() : LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        LocalDateTime twentyFourHoursAgo = now.minus(24, ChronoUnit.HOURS);
        LocalDateTime sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        LocalDateTime thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        String merchantId = transaction.getMerchantId();
        String panHash = transaction.getPanHash();

        // Parallel execution of independent queries
        CompletableFuture<Long> merchantCountFuture = null;
        CompletableFuture<Long> merchantAmountFuture = null;
        CompletableFuture<Long> panCountFuture = null;
        CompletableFuture<Long> panAmountFuture = null;
        CompletableFuture<Long> distinctTerminalsFuture = null;
        CompletableFuture<List<Long>> historicalAmountsFuture = null;
        CompletableFuture<LocalDateTime> lastTxnFuture = null;

        if (merchantId != null) {
            merchantCountFuture = CompletableFuture.supplyAsync(() ->
                transactionRepository.countByMerchantInTimeWindow(merchantId, oneHourAgo, now));
            merchantAmountFuture = CompletableFuture.supplyAsync(() ->
                transactionRepository.sumAmountByMerchantInTimeWindow(merchantId, twentyFourHoursAgo, now));
        }

        if (panHash != null) {
            panCountFuture = CompletableFuture.supplyAsync(() ->
                transactionRepository.countByPanInTimeWindow(panHash, oneHourAgo, now));
            panAmountFuture = CompletableFuture.supplyAsync(() ->
                transactionRepository.sumAmountByPanInTimeWindow(panHash, sevenDaysAgo, now));
            distinctTerminalsFuture = CompletableFuture.supplyAsync(() ->
                transactionRepository.countDistinctTerminalsByPan(panHash, thirtyDaysAgo, now));
            historicalAmountsFuture = CompletableFuture.supplyAsync(() ->
                transactionRepository.findRecentAmountsByPanBefore(
                        panHash, thirtyDaysAgo, now, PageRequest.of(0, 1_000)));
            lastTxnFuture = CompletableFuture.supplyAsync(() ->
                transactionRepository.findLastTransactionTimeByPanBefore(panHash, now));
        }

        // Wait for all futures and populate features
        try {
            if (merchantCountFuture != null) {
                features.put("merchant_txn_count_1h", merchantCountFuture.get());
            }
            if (merchantAmountFuture != null) {
                Long amount = merchantAmountFuture.get();
                features.put("merchant_txn_amount_sum_24h", amount != null ? amount / 100.0 : 0.0);
            }
            if (panCountFuture != null) {
                features.put("pan_txn_count_1h", panCountFuture.get());
            }
            if (panAmountFuture != null) {
                Long amount = panAmountFuture.get();
                features.put("pan_txn_amount_sum_7d", amount != null ? amount / 100.0 : 0.0);
            }
            if (distinctTerminalsFuture != null) {
                features.put("distinct_terminals_last_30d_for_pan", distinctTerminalsFuture.get());
            }
            if (historicalAmountsFuture != null) {
                AmountStatistics statistics = calculateAmountStatistics(historicalAmountsFuture.get());
                features.put("avg_amount_by_pan_30d", statistics.meanCents() / 100.0);
                Long amountCents = transaction.getAmountCents();
                double zScore = amountCents != null && statistics.standardDeviationCents() > 0
                        ? (amountCents - statistics.meanCents()) / statistics.standardDeviationCents()
                        : 0.0;
                features.put("zscore_amount_vs_pan_history", zScore);
            }
            if (lastTxnFuture != null && transaction.getTxnTs() != null) {
                LocalDateTime lastTxn = lastTxnFuture.get();
                if (lastTxn != null) {
                    long minutesSince = ChronoUnit.MINUTES.between(lastTxn, transaction.getTxnTs());
                    features.put("time_since_last_txn_for_pan_minutes", minutesSince);
                } else {
                    features.put("time_since_last_txn_for_pan_minutes", -1);
                }
            }
        } catch (Exception e) {
            logger.warn("Error in parallel feature extraction for transaction {}", transaction.getTxnId(), e);
            // Fallback to sequential
            extractBehavioralFeatures(transaction, features);
        }
    }

    /**
     * Cached merchant velocity lookup
     */
    @Cacheable(value = "aggregateFeatures", key = "'merchant:' + #merchantId + ':count:1h'")
    public Long getCachedMerchantCount(String merchantId, LocalDateTime start, LocalDateTime end) {
        return transactionRepository.countByMerchantInTimeWindow(merchantId, start, end);
    }

    /**
     * Cached PAN velocity lookup
     */
    @Cacheable(value = "aggregateFeatures", key = "'pan:' + #panHash + ':count:1h'")
    public Long getCachedPanCount(String panHash, LocalDateTime start, LocalDateTime end) {
        return transactionRepository.countByPanInTimeWindow(panHash, start, end);
    }

    // Delegate to original FeatureExtractionService methods
    private void extractTransactionFeatures(TransactionEntity transaction, Map<String, Object> features) {
        Long amountCents = transaction.getAmountCents();
        if (amountCents != null) {
            double amount = amountCents / 100.0;
            features.put("amount", amount);
            features.put("log_amount", Math.log(Math.max(amount, 0.01)));
        }
        features.put("currency", transaction.getCurrency() != null ? transaction.getCurrency() : "USD");
        features.put("merchant_id", transaction.getMerchantId());
        features.put("terminal_id", transaction.getTerminalId());
        // pspId so RulesExecutionService evaluates THIS PSP's own rule copies (not just the system
        // defaults) on the ultra/production path — mirrors FeatureExtractionService.
        features.put("pspId", transaction.getPspId());
        
        String panHash = transaction.getPanHash();
        if (panHash != null && panHash.length() >= 6) {
            features.put("card_bin_hash", panHash.substring(0, 6));
        }
        
        LocalDateTime txnTime = transaction.getTxnTs();
        if (txnTime != null) {
            features.put("txn_hour_of_day", txnTime.getHour());
            features.put("txn_day_of_week", txnTime.getDayOfWeek().getValue());
        }
    }

    private void extractBehavioralFeatures(TransactionEntity transaction, Map<String, Object> features) {
        LocalDateTime now = transaction.getTxnTs() != null ? transaction.getTxnTs() : LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        LocalDateTime twentyFourHoursAgo = now.minus(24, ChronoUnit.HOURS);
        LocalDateTime sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        LocalDateTime thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        String merchantId = transaction.getMerchantId();
        if (merchantId != null) {
            features.put("merchant_txn_count_1h", 
                transactionRepository.countByMerchantInTimeWindow(merchantId, oneHourAgo, now));
            Long amount = transactionRepository.sumAmountByMerchantInTimeWindow(merchantId, twentyFourHoursAgo, now);
            features.put("merchant_txn_amount_sum_24h", amount != null ? amount / 100.0 : 0.0);
        }

        String panHash = transaction.getPanHash();
        if (panHash != null) {
            features.put("pan_txn_count_1h", 
                transactionRepository.countByPanInTimeWindow(panHash, oneHourAgo, now));
            Long amount = transactionRepository.sumAmountByPanInTimeWindow(panHash, sevenDaysAgo, now);
            features.put("pan_txn_amount_sum_7d", amount != null ? amount / 100.0 : 0.0);
            features.put("distinct_terminals_last_30d_for_pan",
                transactionRepository.countDistinctTerminalsByPan(panHash, thirtyDaysAgo, now));
            AmountStatistics statistics = calculateAmountStatistics(
                    transactionRepository.findRecentAmountsByPanBefore(
                            panHash, thirtyDaysAgo, now, PageRequest.of(0, 1_000)));
            features.put("avg_amount_by_pan_30d", statistics.meanCents() / 100.0);
            Long amountCents = transaction.getAmountCents();
            double zScore = amountCents != null && statistics.standardDeviationCents() > 0
                    ? (amountCents - statistics.meanCents()) / statistics.standardDeviationCents()
                    : 0.0;
            features.put("zscore_amount_vs_pan_history", zScore);
            
            LocalDateTime lastTxn = transactionRepository.findLastTransactionTimeByPanBefore(panHash, now);
            if (lastTxn != null && transaction.getTxnTs() != null) {
                long minutesSince = ChronoUnit.MINUTES.between(lastTxn, transaction.getTxnTs());
                features.put("time_since_last_txn_for_pan_minutes", minutesSince);
            } else {
                features.put("time_since_last_txn_for_pan_minutes", -1);
            }
        }
    }

    private void extractEmvFeatures(TransactionEntity transaction, Map<String, Object> features) {
        if (transaction.getEmvTags() == null || transaction.getEmvTags().isEmpty()) {
            features.put("is_chip_present", false);
            features.put("is_contactless", false);
            features.put("cvm_method", 0);
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> emvTags = objectMapper.readValue(transaction.getEmvTags(), Map.class);
            features.put("is_chip_present", emvTags.containsKey("9F7A") || emvTags.containsKey("95"));
            features.put("is_contactless", emvTags.containsKey("9F6E") || 
                        (emvTags.containsKey("82") && emvTags.get("82").toString().contains("contactless")));
            features.put("cvm_method", emvTags.containsKey("9F34") ? 
                parseCvmMethod(emvTags.get("9F34").toString()) : 0);
            features.put("aip_flags", emvTags.containsKey("82") ? emvTags.get("82") : 0);
            features.put("aid", emvTags.containsKey("4F") ? emvTags.get("4F") : "");
            features.put("approval_code_present", emvTags.containsKey("8A"));
        } catch (Exception e) {
            logger.warn("Failed to parse EMV tags", e);
            features.put("is_chip_present", false);
            features.put("is_contactless", false);
            features.put("cvm_method", 0);
        }
    }

    private void extractAmlFeatures(TransactionEntity transaction, Map<String, Object> features) {
        LocalDateTime now = transaction.getTxnTs() != null ? transaction.getTxnTs() : LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
        LocalDateTime sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);

        String panHash = transaction.getPanHash();
        if (panHash != null) {
            Long inboundAmount = transactionRepository.sumInboundAmountByPanInWindow(
                    panHash, thirtyDaysAgo, now);
            Long outboundAmount = transactionRepository.sumOutboundAmountByPanInWindow(
                    panHash, thirtyDaysAgo, now);
            features.put("cumulative_credits_30d", inboundAmount != null ? inboundAmount / 100.0 : 0.0);
            features.put("cumulative_debits_30d", outboundAmount != null ? outboundAmount / 100.0 : 0.0);
            features.put("num_high_value_txn_7d",
                transactionRepository.countHighValueByPanInWindow(
                        panHash, highValueAmountCents, sevenDaysAgo, now));
        }
    }

    private AmountStatistics calculateAmountStatistics(List<Long> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            return new AmountStatistics(0.0, 0.0);
        }
        double mean = amounts.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = amounts.stream()
                .mapToDouble(amount -> {
                    double difference = amount - mean;
                    return difference * difference;
                })
                .average()
                .orElse(0.0);
        return new AmountStatistics(mean, Math.sqrt(variance));
    }

    private record AmountStatistics(double meanCents, double standardDeviationCents) {}

    /**
     * Parse the CVM method into the SAME categorical encoding as
     * {@code FeatureExtractionService.parseCvmMethod} (0=unknown, 1=PIN, 2=signature,
     * 3=no-CVM). Previously this returned the raw first CVMR byte (0-255), so the two
     * extraction paths emitted different value domains for the same {@code cvm_method}
     * feature and produced inconsistent scores for identical EMV input.
     */
    private int parseCvmMethod(Object cvmrValue) {
        if (cvmrValue == null) {
            return 0;
        }
        try {
            String clean = cvmrValue.toString().replaceAll("[^0-9A-Fa-f]", "");
            if (clean.length() < 2) {
                return 0;
            }
            int firstByte = Integer.parseInt(clean.substring(0, 2), 16);
            int cvmCode = firstByte & 0x3F; // strip the two "apply-if-fail" flag bits
            if (cvmCode == 0x1E || cvmCode == 0x02 || cvmCode == 0x04 || cvmCode == 0x1A) {
                return 1; // PIN
            }
            if (cvmCode == 0x3E) {
                return 2; // Signature
            }
            if (cvmCode == 0x00 || cvmCode == 0x3F) {
                return 3; // No CVM required / performed
            }
            return 0; // Unknown
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse CVM method from value: {}", cvmrValue, e);
            return 0;
        }
    }
}

