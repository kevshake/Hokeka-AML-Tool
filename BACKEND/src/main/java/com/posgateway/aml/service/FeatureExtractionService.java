package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private final com.posgateway.aml.service.rules.RuleFeatureEnrichmentService ruleFeatureEnrichmentService;
    private final com.posgateway.aml.service.enrichment.IpReputationService ipReputationService;

    @Value("${aml.features.high-value-amount-cents:1000000}")
    private long highValueAmountCents = 1_000_000L;

    @Autowired
    public FeatureExtractionService(TransactionRepository transactionRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) com.posgateway.aml.service.graph.Neo4jGdsService neo4jGdsService,
            com.posgateway.aml.service.rules.RuleFeatureEnrichmentService ruleFeatureEnrichmentService,
            com.posgateway.aml.service.enrichment.IpReputationService ipReputationService) {
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
        this.neo4jGdsService = neo4jGdsService;
        this.ruleFeatureEnrichmentService = ruleFeatureEnrichmentService;
        this.ipReputationService = ipReputationService;
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

        // IP reputation (VPN / proxy / datacenter / manipulated source) — deterministic, no I/O
        extractIpReputationFeatures(transaction, features);

        // Graph features from Neo4j GDS (PageRank, Community, Betweenness)
        extractGraphFeatures(transaction, features);

        // SpEL rule features (velocity, screening, chargeback signals)
        ruleFeatureEnrichmentService.enrich(transaction, features);

        logger.debug("Extracted {} features for transaction {}", features.size(), transaction.getTxnId());
        return features;
    }

    /**
     * IP-reputation features so the ML model and the dynamic per-PSP rules can validate/mitigate on
     * the connection IP. Only the deterministic (no-network) signals run here to keep the hot path
     * fast: VPN/proxy/datacenter membership and manipulated/masked (private/reserved/malformed)
     * sources. A rule can then be written as e.g. {@code #features['ip_vpn_or_proxy'] == true}.
     */
    private void extractIpReputationFeatures(TransactionEntity transaction, Map<String, Object> features) {
        String ip = transaction.getIpAddress();
        // Deterministic signals only (no GeoIP lookup) → no declared/geo country needed here.
        var assessment = ipReputationService.assess(ip, null, null);
        features.put("ip_present", assessment.present());
        features.put("ip_vpn_or_proxy", assessment.anonymizing());
        features.put("ip_manipulated", assessment.malformed() || assessment.privateOrReserved());
        features.put("ip_reputation", assessment.category());
    }

    /**
     * Extract graph-based features from Neo4j GDS.
     * These are computed by Neo4j algorithms and returned by the graph service.
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
        features.put("pspId", transaction.getPspId());
        features.put("channel", transaction.getChannelType());
        features.put("country_code", transaction.getMerchantCountry());
        features.put("cash_transaction", transaction.isCashTransaction());
        features.put("pan_hash", transaction.getPanHash());

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
        LocalDateTime now = transaction.getTxnTs() != null ? transaction.getTxnTs() : LocalDateTime.now();
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
            List<Long> historicalAmounts = transactionRepository.findRecentAmountsByPanBefore(
                    panHash, thirtyDaysAgo, now, PageRequest.of(0, 1_000));
            AmountStatistics amountStatistics = calculateAmountStatistics(historicalAmounts);
            LocalDateTime lastTxnTime = transactionRepository.findLastTransactionTimeByPanBefore(
                    panHash, now);

            features.put("pan_txn_count_1h", panTxnCount1h != null ? panTxnCount1h : 0L);
            features.put("pan_txn_amount_sum_7d", panAmountSum7d != null ? panAmountSum7d / 100.0 : 0.0);
            features.put("distinct_terminals_last_30d_for_pan",
                    distinctTerminals30d != null ? distinctTerminals30d : 0L);
            features.put("avg_amount_by_pan_30d", amountStatistics.meanCents() / 100.0);

            // Time since last transaction
            if (lastTxnTime != null && transaction.getTxnTs() != null) {
                long minutesSince = ChronoUnit.MINUTES.between(lastTxnTime, transaction.getTxnTs());
                features.put("time_since_last_txn_for_pan_minutes", minutesSince);
            } else {
                features.put("time_since_last_txn_for_pan_minutes", -1); // New card
            }

            Long amountCents = transaction.getAmountCents();
            double zScore = amountCents != null && amountStatistics.standardDeviationCents() > 0
                    ? (amountCents - amountStatistics.meanCents()) / amountStatistics.standardDeviationCents()
                    : 0.0;
            features.put("zscore_amount_vs_pan_history", zScore);
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

            Long highValueCount = transactionRepository.countHighValueByPanInWindow(
                    panHash, highValueAmountCents, sevenDaysAgo, now);
            features.put("num_high_value_txn_7d", highValueCount != null ? highValueCount : 0L);
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

    private int parseCvmMethod(Object cvmrValue) {
        if (cvmrValue == null) {
            return 0;
        }
        try {
            // CVMR is a 6-character hex string (3 bytes).
            // Byte 1 (first two hex chars): CVM performed.
            //   Bits 7-6 are "apply if fail" flags; bits 5-0 encode the CVM code.
            // Known CVM codes (bits 5-0):
            //   0x1E = PIN verified by ICC (online or offline)
            //   0x02 = Offline PIN plaintext
            //   0x04 = Offline PIN encrypted
            //   0x1A = Online PIN
            //   0x3E = Signature (paper)
            //   0x00 = No CVM required
            //   0x3F = No CVM performed
            String clean = cvmrValue.toString().replaceAll("[^0-9A-Fa-f]", "");
            if (clean.length() < 2) {
                return 0;
            }
            int firstByte = Integer.parseInt(clean.substring(0, 2), 16);
            // Strip the two "apply-if-fail" flag bits (7-6) to get the raw CVM code.
            int cvmCode = firstByte & 0x3F;
            if (cvmCode == 0x1E || cvmCode == 0x02 || cvmCode == 0x04 || cvmCode == 0x1A) {
                return 1; // PIN (ICC offline, plaintext offline, encrypted offline, online)
            }
            if (cvmCode == 0x3E) {
                return 2; // Signature (paper)
            }
            if (cvmCode == 0x00 || cvmCode == 0x3F) {
                return 3; // No CVM required / no CVM performed
            }
            return 0; // Unknown or unrecognised CVM code
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse CVM method from value: {}", cvmrValue, e);
            return 0;
        }
    }
}
