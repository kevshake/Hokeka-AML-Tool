package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Feature Extraction Service
 * Extracts features from transactions for model scoring
 * Includes behavioral and velocity features with caching
 */
@Service
public class FeatureExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(FeatureExtractionService.class);

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final com.posgateway.aml.service.graph.Neo4jGdsService neo4jGdsService;

    @Autowired
    public FeatureExtractionService(TransactionRepository transactionRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) com.posgateway.aml.service.graph.Neo4jGdsService neo4jGdsService) {
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
        this.neo4jGdsService = neo4jGdsService;
    }

    /**
     * Extract features from transaction
     * 
     * @param transaction Transaction entity
     * @return Map of feature names to values
     */
    public Map<String, Object> extractFeatures(TransactionEntity transaction) {
        logger.debug("Extracting features for transaction: {}", transaction.getTxnId());

        Map<String, Object> features = new HashMap<>();

        // Transaction-level features
        extractTransactionFeatures(transaction, features);

        // Behavioral features (velocity, aggregates)
        extractBehavioralFeatures(transaction, features);

        // EMV-specific features
        extractEmvFeatures(transaction, features);

        // AML-specific features
        extractAmlFeatures(transaction, features);

        // Graph features from Neo4j GDS (PageRank, Community, Betweenness)
        extractGraphFeatures(transaction, features);

        logger.debug("Extracted {} features for transaction {}", features.size(), transaction.getTxnId());
        return features;
    }

    /**
     * Extract graph-based features from Neo4j GDS.
     * These are computed by Neo4j algorithms and cached in Aerospike.
     */
    private void extractGraphFeatures(TransactionEntity transaction, Map<String, Object> features) {
        if (neo4jGdsService == null) {
            // Neo4j not enabled - set default values
            features.put("pageRank", 0.0);
            features.put("communityId", 0L);
            features.put("betweenness", 0.0);
            features.put("connectionCount", 0L);
            features.put("triangle_count", 0L);
            features.put("clustering_coefficient", 0.0);
            return;
        }

        String merchantId = transaction.getMerchantId();
        if (merchantId == null) {
            return;
        }

        try {
            Map<String, Object> graphMetrics = neo4jGdsService.getGraphMetrics(merchantId);
            if (graphMetrics != null) {
                features.put("pageRank", graphMetrics.getOrDefault("pageRank", 0.0));
                features.put("communityId", graphMetrics.getOrDefault("communityId", 0L));
                features.put("betweenness", graphMetrics.getOrDefault("betweenness", 0.0));
                features.put("connectionCount", graphMetrics.getOrDefault("connectionCount", 0L));
                features.put("triangle_count", graphMetrics.getOrDefault("triangleCount", 0L));
                features.put("clustering_coefficient", graphMetrics.getOrDefault("localClusteringCoefficient", 0.0));
                logger.debug("Added graph features for merchant {}: pageRank={}, clustering={}", merchantId,
                        graphMetrics.get("pageRank"), graphMetrics.get("localClusteringCoefficient"));
            }
        } catch (Exception e) {
            logger.warn("Error extracting graph features for merchant {}: {}", merchantId, e.getMessage());
        }
    }

    private void extractTransactionFeatures(TransactionEntity transaction, Map<String, Object> features) {
        // Amount features - cache amountCents to avoid repeated method calls
        Long amountCents = transaction.getAmountCents();
        if (amountCents != null) {
            double amount = amountCents / 100.0;
            features.put("amount", amount);
            features.put("log_amount", Math.log(Math.max(amount, 0.01)));
        }

        // Currency - use null-safe default
        String currency = transaction.getCurrency();
        features.put("currency", currency != null ? currency : "USD");

        // Merchant features
        features.put("merchant_id", transaction.getMerchantId());
        features.put("terminal_id", transaction.getTerminalId());

        // Card BIN (first 6 digits) - extract from PAN hash if available
        String panHash = transaction.getPanHash();
        if (panHash != null && panHash.length() >= 6) {
            features.put("card_bin_hash", panHash.substring(0, 6));
        }

        // Time features
        LocalDateTime txnTime = transaction.getTxnTs();
        if (txnTime != null) {
            features.put("txn_hour_of_day", txnTime.getHour());
            features.put("txn_day_of_week", txnTime.getDayOfWeek().getValue());
        }
    }

    private void extractBehavioralFeatures(TransactionEntity transaction, Map<String, Object> features) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        LocalDateTime twentyFourHoursAgo = now.minus(24, ChronoUnit.HOURS);
        LocalDateTime sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        LocalDateTime thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        // Merchant velocity features - cache merchantId for performance
        String merchantId = transaction.getMerchantId();
        if (merchantId != null) {
            Long merchantTxnCount1h = transactionRepository.countByMerchantInTimeWindow(
                    merchantId, oneHourAgo, now);
            Long merchantAmountSum24h = transactionRepository.sumAmountByMerchantInTimeWindow(
                    merchantId, twentyFourHoursAgo, now);

            features.put("merchant_txn_count_1h", merchantTxnCount1h);
            features.put("merchant_txn_amount_sum_24h",
                    merchantAmountSum24h != null ? merchantAmountSum24h / 100.0 : 0.0);
        }

        // PAN velocity features - cache panHash for performance
        String panHash = transaction.getPanHash();
        if (panHash != null) {
            Long panTxnCount1h = transactionRepository.countByPanInTimeWindow(
                    panHash, oneHourAgo, now);
            Long panAmountSum7d = transactionRepository.sumAmountByPanInTimeWindow(
                    panHash, sevenDaysAgo, now);
            Long distinctTerminals30d = transactionRepository.countDistinctTerminalsByPan(
                    panHash, thirtyDaysAgo, now);
            Double avgAmount30d = transactionRepository.avgAmountByPanInTimeWindow(
                    panHash, thirtyDaysAgo, now);
            LocalDateTime lastTxnTime = transactionRepository.findLastTransactionTimeByPan(
                    panHash);

            features.put("pan_txn_count_1h", panTxnCount1h != null ? panTxnCount1h : 0L);
            features.put("pan_txn_amount_sum_7d", panAmountSum7d != null ? panAmountSum7d / 100.0 : 0.0);
            features.put("distinct_terminals_last_30d_for_pan",
                    distinctTerminals30d != null ? distinctTerminals30d : 0L);
            features.put("avg_amount_by_pan_30d", avgAmount30d != null ? avgAmount30d / 100.0 : 0.0);

            // Time since last transaction
            if (lastTxnTime != null && transaction.getTxnTs() != null) {
                long minutesSince = ChronoUnit.MINUTES.between(lastTxnTime, transaction.getTxnTs());
                features.put("time_since_last_txn_for_pan_minutes", minutesSince);
            } else {
                features.put("time_since_last_txn_for_pan_minutes", -1); // New card
            }

            // Z-score of amount vs PAN history - optimize division operations
            // Note: amountCents already cached in extractTransactionFeatures, but this is a
            // different method
            // Cache again here for this method's scope
            Long amountCents = transaction.getAmountCents();
            if (avgAmount30d != null && avgAmount30d > 0 && amountCents != null) {
                double currentAmount = amountCents / 100.0;
                double avgAmount = avgAmount30d / 100.0;
                // Simplified z-score (would need std dev for proper calculation)
                double maxAvg = Math.max(avgAmount, 1.0);
                double zScore = (currentAmount - avgAmount) / maxAvg;
                features.put("zscore_amount_vs_pan_history", zScore);
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

            // Extract EMV-specific features
            features.put("is_chip_present", emvTags.containsKey("9F7A") || emvTags.containsKey("95"));
            features.put("is_contactless", emvTags.containsKey("9F6E") ||
                    (emvTags.containsKey("82") && emvTags.get("82").toString().contains("contactless")));

            // CVM method from CVMR (Tag 9F34)
            if (emvTags.containsKey("9F34")) {
                features.put("cvm_method", parseCvmMethod(emvTags.get("9F34").toString()));
            } else {
                features.put("cvm_method", 0);
            }

            // AIP flags (Tag 82)
            features.put("aip_flags", emvTags.containsKey("82") ? emvTags.get("82") : 0);

            // AID (Application ID - Tag 4F)
            features.put("aid", emvTags.containsKey("4F") ? emvTags.get("4F") : "");

            // Approval code present
            features.put("approval_code_present", emvTags.containsKey("8A"));

        } catch (Exception e) {
            logger.warn("Failed to parse EMV tags for transaction {}", transaction.getTxnId(), e);
            features.put("is_chip_present", false);
            features.put("is_contactless", false);
            features.put("cvm_method", 0);
        }
    }

    private void extractAmlFeatures(TransactionEntity transaction, Map<String, Object> features) {
        // Cache time calculations for performance
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
        LocalDateTime sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);

        // Cache panHash for performance
        String panHash = transaction.getPanHash();
        if (panHash != null) {
            // Cumulative debits/credits (simplified - would need transaction type)
            Long panAmountSum30d = transactionRepository.sumAmountByPanInTimeWindow(
                    panHash, thirtyDaysAgo, now);
            features.put("cumulative_debits_30d", panAmountSum30d != null ? panAmountSum30d / 100.0 : 0.0);

            // High value transaction count
            Long highValueCount = transactionRepository.countByPanInTimeWindow(
                    panHash, sevenDaysAgo, now);
            features.put("num_high_value_txn_7d", highValueCount != null ? highValueCount : 0L);
        }
    }

    private int parseCvmMethod(Object cvmrValue) {
        // Simplified CVM parsing - CVMR is typically 3 bytes
        // This is a placeholder - actual implementation would parse CVMR properly
        if (cvmrValue == null) {
            return 0;
        }
        try {
            String cvmrStr = cvmrValue.toString();
            if (cvmrStr.length() >= 2) {
                return Integer.parseInt(cvmrStr.substring(0, 2), 16);
            }
        } catch (Exception e) {
            logger.debug("Failed to parse CVM method", e);
        }
        return 0;
    }
}
