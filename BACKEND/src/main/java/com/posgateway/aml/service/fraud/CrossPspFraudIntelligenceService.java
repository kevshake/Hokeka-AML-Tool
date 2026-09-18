package com.posgateway.aml.service.fraud;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.psp.CrossPspFraudFlag;
import com.posgateway.aml.model.AlertDisposition;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.psp.CrossPspFraudFlagRepository;
import com.posgateway.aml.service.sanctions.NameMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cross-PSP Fraud Intelligence Service.
 *
 * <p><b>Purpose:</b> When one PSP's merchant is confirmed as fraudulent, all
 * other PSPs are automatically protected. New transactions hitting a flagged
 * entity (same merchant, PAN hash, terminal, email, or similar name) get
 * flagged or blocked in real-time.
 *
 * <p><b>Auto-population:</b> Called from {@code AlertFraudIncidentBridge} when
 * an alert is disposed as TRUE_POSITIVE. This service creates or updates
 * {@link CrossPspFraudFlag} entries for the merchant ID, PAN hash, terminal,
 * and merchant name (via fuzzy matching).
 *
 * <p><b>Consumption:</b> Called from {@code DecisionEngine.checkHardRules()}
 * and {@code FraudDetectionService} during transaction evaluation.
 */
@Service
public class CrossPspFraudIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(CrossPspFraudIntelligenceService.class);

    private final CrossPspFraudFlagRepository flagRepository;
    private final AlertRepository alertRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final NameMatchingService nameMatchingService;

    public CrossPspFraudIntelligenceService(CrossPspFraudFlagRepository flagRepository,
                                             AlertRepository alertRepository,
                                             TransactionRepository transactionRepository,
                                             MerchantRepository merchantRepository,
                                             NameMatchingService nameMatchingService) {
        this.flagRepository = flagRepository;
        this.alertRepository = alertRepository;
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.nameMatchingService = nameMatchingService;
    }

    // =========================================================================
    // WRITE: Auto-populate from confirmed fraud (called by AlertFraudIncidentBridge)
    // =========================================================================

    /**
     * Flag entities associated with a confirmed fraudulent alert across all PSPs.
     * Extracts merchant ID, PAN hash, terminal, and merchant name from the
     * underlying transaction and creates/updates CrossPspFraudFlag entries.
     */
    @Transactional
    public void flagEntitiesFromAlert(Long alertId, Long sourcePspId, AlertDisposition disposition) {
        Alert alert = alertRepository.findById(alertId).orElse(null);
        if (alert == null) {
            log.warn("Cannot flag entities: alert {} not found", alertId);
            return;
        }

        Long txnId = alert.getTxnId();
        if (txnId == null) {
            // No transaction link — flag the merchant ID from the alert itself
            if (alert.getMerchantId() != null) {
                flagEntity(String.valueOf(alert.getMerchantId()), "MERCHANT_ID",
                        sourcePspId, alertId, disposition, "Flagged from alert");
            }
            return;
        }

        TransactionEntity txn = transactionRepository.findById(txnId).orElse(null);
        if (txn == null) {
            log.warn("Cannot flag entities: transaction {} not found for alert {}", txnId, alertId);
            return;
        }

        List<String> reasons = new ArrayList<>();

        // 1. Merchant ID
        if (txn.getMerchantId() != null) {
            flagEntity(txn.getMerchantId(), "MERCHANT_ID", sourcePspId, alertId, disposition,
                    "Merchant ID from confirmed fraudulent transaction");
        }

        // 2. PAN hash (card number)
        if (txn.getPanHash() != null) {
            flagEntity(txn.getPanHash(), "PAN_HASH", sourcePspId, alertId, disposition,
                    "PAN hash from confirmed fraudulent transaction");
        }

        // 3. Terminal ID
        if (txn.getTerminalId() != null) {
            flagEntity(txn.getTerminalId(), "TERMINAL", sourcePspId, alertId, disposition,
                    "Terminal from confirmed fraudulent transaction");
        }

        // 4. Merchant name (resolved from merchant ID) for fuzzy matching
        if (txn.getMerchantId() != null) {
            try {
                long merchantIdLong = Long.parseLong(txn.getMerchantId());
                java.util.Optional<com.posgateway.aml.entity.merchant.Merchant> merchantOpt =
                    merchantRepository.findById(merchantIdLong);
                if (merchantOpt.isPresent()) {
                    String name = merchantOpt.get().getTradingName();
                    if (name != null) {
                        flagEntity(name, "NAME", sourcePspId, alertId, disposition,
                                "Merchant name from confirmed fraudulent transaction");
                    }
                }
            } catch (NumberFormatException e) {
                log.debug("Merchant ID {} is not numeric, skipping name resolution", txn.getMerchantId());
            }
        }

        log.info("Flagged {} entities from alert #{} (PSP {})", reasons.size(), alertId, sourcePspId);
    }

    private void flagEntity(String entityValue, String entityType, Long sourcePspId,
                             Long alertId, AlertDisposition disposition, String reason) {
        Optional<CrossPspFraudFlag> existing = flagRepository.findByEntityValueAndEntityType(entityValue, entityType);

        if (existing.isPresent()) {
            CrossPspFraudFlag flag = existing.get();
            flag.setFlagCount(flag.getFlagCount() + 1);
            flag.setLastFlagged(LocalDateTime.now());
            if (flag.getRiskLevel().equals("MEDIUM")) {
                flag.setRiskLevel("HIGH"); // Escalate on repeat flags
            }
            flag.setReason(reason);
            flagRepository.save(flag);
            log.debug("Updated cross-PSP flag: {} ({}) — count now {}", entityValue, entityType, flag.getFlagCount());
        } else {
            CrossPspFraudFlag flag = CrossPspFraudFlag.builder()
                    .entityValue(entityValue)
                    .entityType(entityType)
                    .flagCount(1)
                    .sourcePspId(sourcePspId)
                    .sourceAlertId(alertId)
                    .riskLevel("HIGH")
                    .reason(reason)
                    .build();
            flagRepository.save(flag);
            log.info("Created cross-PSP flag: {} ({}) from PSP {}", entityValue, entityType, sourcePspId);
        }
    }

    // =========================================================================
    // READ: Check a transaction against all known cross-PSP flags
    // =========================================================================

    /**
     * Result of a cross-PSP intelligence check.
     */
    public static class ScreeningResult {
        private final boolean hasMatch;
        private final List<String> matchedEntities;
        private final int totalFlagCount;
        private final String highestRiskLevel;

        public ScreeningResult(boolean hasMatch, List<String> matchedEntities,
                                int totalFlagCount, String highestRiskLevel) {
            this.hasMatch = hasMatch;
            this.matchedEntities = matchedEntities;
            this.totalFlagCount = totalFlagCount;
            this.highestRiskLevel = highestRiskLevel;
        }

        public boolean hasMatch() { return hasMatch; }
        public List<String> getMatchedEntities() { return matchedEntities; }
        public int getTotalFlagCount() { return totalFlagCount; }
        public String getHighestRiskLevel() { return highestRiskLevel; }
        public boolean isCritical() { return "CRITICAL".equals(highestRiskLevel); }
        public boolean isHigh() { return "HIGH".equals(highestRiskLevel) || isCritical(); }
    }

    /**
     * Screen a transaction against the cross-PSP fraud intelligence database.
     * Checks merchant ID, PAN hash, terminal, and fuzzy merchant name.
     *
     * @return ScreeningResult with match details and risk level
     */
    @Transactional(readOnly = true)
    public ScreeningResult screenTransaction(TransactionEntity txn) {
        List<String> matchedEntities = new ArrayList<>();
        int totalFlagCount = 0;
        String worstRisk = null;

        // Collect identifiers from the transaction
        List<String> identifiers = new ArrayList<>();
        if (txn.getMerchantId() != null) identifiers.add(txn.getMerchantId());
        if (txn.getPanHash() != null) identifiers.add(txn.getPanHash());
        if (txn.getTerminalId() != null) identifiers.add(txn.getTerminalId());

        // Check exact matches
        for (String id : identifiers) {
            String type = resolveEntityType(id, txn);
            java.util.Optional<CrossPspFraudFlag> flagOpt = flagRepository.findByEntityValueAndEntityType(id, type);
            if (flagOpt.isPresent()) {
                CrossPspFraudFlag flag = flagOpt.get();
                matchedEntities.add(type + "=" + id);
                totalFlagCount += flag.getFlagCount();
                if (worstRisk == null || compareRisk(flag.getRiskLevel(), worstRisk) > 0) {
                    worstRisk = flag.getRiskLevel();
                }
            }
        }

        // Fuzzy name matching: compare merchant name against flagged NAME entries
        if (txn.getMerchantId() != null) {
            try {
                java.util.Optional<com.posgateway.aml.entity.merchant.Merchant> merchantOpt =
                    merchantRepository.findById(Long.parseLong(txn.getMerchantId()));
                if (merchantOpt.isPresent()) {
                    String merchantName = merchantOpt.get().getTradingName();
                    if (merchantName != null) {
                        List<CrossPspFraudFlag> nameFlags = flagRepository
                                .findByEntityTypeAndLastFlaggedAfter("NAME", LocalDateTime.now().minusDays(365));
                        for (CrossPspFraudFlag nameFlag : nameFlags) {
                            double similarity = nameMatchingService.calculateSimilarityScore(
                                    merchantName, nameFlag.getEntityValue());
                            if (similarity >= 0.8) {
                                matchedEntities.add("NAME_MATCH(" + String.format("%.2f", similarity)
                                        + ")=" + nameFlag.getEntityValue());
                                totalFlagCount += nameFlag.getFlagCount();
                                if (worstRisk == null || compareRisk(nameFlag.getRiskLevel(), worstRisk) > 0) {
                                    worstRisk = nameFlag.getRiskLevel();
                                }
                            }
                        }
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        boolean hasMatch = !matchedEntities.isEmpty();
        return new ScreeningResult(hasMatch, matchedEntities, totalFlagCount,
                worstRisk != null ? worstRisk : "NONE");
    }

    private String resolveEntityType(String value, TransactionEntity txn) {
        if (value.equals(txn.getMerchantId())) return "MERCHANT_ID";
        if (value.equals(txn.getPanHash())) return "PAN_HASH";
        if (value.equals(txn.getTerminalId())) return "TERMINAL";
        return "UNKNOWN";
    }

    private int compareRisk(String a, String b) {
        return Integer.compare(riskOrdinal(a), riskOrdinal(b));
    }

    private int riskOrdinal(String r) {
        if (r == null) return 0;
        return switch (r.toUpperCase()) {
            case "CRITICAL" -> 5;
            case "HIGH" -> 4;
            case "MEDIUM" -> 3;
            case "LOW" -> 2;
            default -> 1;
        };
    }
}