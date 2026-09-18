package com.posgateway.aml.service;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.model.RiskAssessment;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.model.TransactionStatus;
import com.posgateway.aml.service.compliance.CashStructuringDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Risk Assessment Service
 * Orchestrates AML and Fraud detection services to provide comprehensive risk
 * assessment
 */
@Service
public class RiskAssessmentService {

    private static final Logger logger = LoggerFactory.getLogger(RiskAssessmentService.class);

    /**
     * Similarity threshold above which a transaction's payer name is considered
     * to match a registered beneficial owner (i.e. NOT third-party). Levenshtein-
     * based normalised similarity ranges 0.0 (no match) to 1.0 (identical).
     */
    private static final double THIRD_PARTY_MATCH_THRESHOLD = 0.85;

    private final AmlService amlService;
    private final FraudDetectionService fraudDetectionService;
    private final com.posgateway.aml.service.risk.RiskRulesEngine riskRulesEngine;
    private final com.posgateway.aml.repository.MerchantRepository merchantRepository;
    private final com.posgateway.aml.repository.BeneficialOwnerRepository beneficialOwnerRepository;
    private final com.posgateway.aml.service.analytics.LinkAnalysisService linkAnalysisService;
    private final com.posgateway.aml.service.analytics.BehavioralProfilingService behavioralProfilingService;
    private final CashStructuringDetectionService cashStructuringDetectionService;

    /** Levenshtein threshold above which we treat the names as "different person". */
    private static final int THIRD_PARTY_LEVENSHTEIN_THRESHOLD = 3;

    @Autowired
    public RiskAssessmentService(AmlService amlService,
            FraudDetectionService fraudDetectionService,
            com.posgateway.aml.service.risk.RiskRulesEngine riskRulesEngine,
            com.posgateway.aml.repository.MerchantRepository merchantRepository,
            com.posgateway.aml.repository.BeneficialOwnerRepository beneficialOwnerRepository,
            com.posgateway.aml.service.analytics.LinkAnalysisService linkAnalysisService,
            com.posgateway.aml.service.analytics.BehavioralProfilingService behavioralProfilingService,
            CashStructuringDetectionService cashStructuringDetectionService) {
        this.amlService = amlService;
        this.fraudDetectionService = fraudDetectionService;
        this.riskRulesEngine = riskRulesEngine;
        this.merchantRepository = merchantRepository;
        this.beneficialOwnerRepository = beneficialOwnerRepository;
        this.linkAnalysisService = linkAnalysisService;
        this.behavioralProfilingService = behavioralProfilingService;
        this.cashStructuringDetectionService = cashStructuringDetectionService;
    }

    /**
     * Perform comprehensive risk assessment combining AML and Fraud detection
     * 
     * @param transaction The transaction to assess
     * @return Combined RiskAssessment with AML and Fraud scores
     */
    public RiskAssessment assessRisk(Transaction transaction) {
        // ... (existing null checks)
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }

        logger.info("Starting comprehensive risk assessment for transaction: {}",
                transaction.getTransactionId());

        // Perform AML risk assessment
        RiskAssessment amlAssessment = amlService.assessAmlRisk(transaction);

        // Perform Fraud risk assessment
        RiskAssessment fraudAssessment = fraudDetectionService.assessFraudRisk(transaction);

        // Combine assessments
        RiskAssessment combinedAssessment = combineAssessments(amlAssessment, fraudAssessment);
        CashStructuringDetectionService.Assessment cashAssessment =
                cashStructuringDetectionService.assess(toTransactionEntity(transaction));
        combinedAssessment.setCtrRequired(cashAssessment.ctrRequired());
        combinedAssessment.setStrRequired(cashAssessment.structuringSuspected());
        combinedAssessment.setRegulatoryEvidence(cashAssessment.evidence());
        if (cashAssessment.ctrRequired()) {
            combinedAssessment.getRiskFactors().add("KENYA_CASH_TRANSACTION_REPORT");
            combinedAssessment.getRecommendations().add(
                    "Include this cash transaction in the weekly FRC CTR filing");
        }
        if (cashAssessment.structuringSuspected()) {
            combinedAssessment.getRiskFactors().add("CASH_STRUCTURING_24H");
            combinedAssessment.setAmlRiskLevel(RiskLevel.HIGH);
        }
        if ("FX_UNAVAILABLE".equals(cashAssessment.status())) {
            combinedAssessment.getRiskFactors().add("CTR_FX_UNAVAILABLE");
            combinedAssessment.getRecommendations().add(
                    "Approve or refresh the regulatory FX rate before release");
            combinedAssessment.setAmlRiskLevel(RiskLevel.HIGH);
        }

        // Run Rule Engine
        com.posgateway.aml.entity.merchant.Merchant merchant = null;
        if (transaction.getMerchantId() != null) {
            merchant = merchantRepository.findById(Long.parseLong(transaction.getMerchantId())).orElse(null);
        }

        if (merchant != null) {
            // Advanced Analysis (Phase 11)
            // Advanced Analysis (Phase 11 & 29)
            java.util.List<String> linkedBlocked = linkAnalysisService.findLinkedBlockedEntities(transaction, merchant);
            boolean isLinked = !linkedBlocked.isEmpty();
            boolean isAnomaly = behavioralProfilingService.isAmountAnomaly(transaction);

            // Shared cash/FX detector supplies the same structuring fact used by runtime rules.
            boolean isStructuring = cashAssessment.structuringSuspected();

            // Third-party / smurf detection: the transacting party's name does not
            // fuzzy-match any registered beneficial owner of the merchant.
            boolean isThirdParty = detectThirdParty(transaction, merchant);

            // Pass facts to engine
            java.util.Map<String, Object> extraFacts = new java.util.HashMap<>();
            extraFacts.put("isLinkedToBlocked", isLinked);
            extraFacts.put("isBehavioralAnomaly", isAnomaly);
            extraFacts.put("isStructuringSuspected", isStructuring);
            extraFacts.put("isThirdPartySuspected", isThirdParty);

            java.util.List<String> triggeredRules = riskRulesEngine.evaluateRisk(transaction, merchant, extraFacts);

            if (!triggeredRules.isEmpty()) {
                combinedAssessment.getRiskFactors().addAll(triggeredRules);
                // If critical rules triggered
                if (triggeredRules.contains("SANCTIONS_MATCH")
                        || triggeredRules.contains("MCC_MISMATCH_SUSPECTED_LAUNDERING")
                        || triggeredRules.contains("LINKED_TO_BLOCKED_ENTITY")
                        || triggeredRules.contains("PEP_TRANSACTION")) {
                    combinedAssessment.setAmlRiskLevel(RiskLevel.HIGH);
                }
            }

        }

        // Determine final decision
        String decision = determineDecision(combinedAssessment);
        combinedAssessment.setDecision(decision);

        logger.info("Risk assessment completed for transaction {}: Decision={}, AML Score={}, Fraud Score={}",
                transaction.getTransactionId(), decision,
                combinedAssessment.getAmlRiskScore(),
                combinedAssessment.getFraudScore());

        return combinedAssessment;
    }

    private RiskAssessment combineAssessments(RiskAssessment amlAssessment,
            RiskAssessment fraudAssessment) {
        // Null safety checks
        if (amlAssessment == null && fraudAssessment == null) {
            logger.warn("Both assessments are null, returning empty assessment");
            return new RiskAssessment();
        }
        if (amlAssessment == null) {
            logger.warn("AML assessment is null, using fraud assessment only");
            return fraudAssessment;
        }
        if (fraudAssessment == null) {
            logger.warn("Fraud assessment is null, using AML assessment only");
            return amlAssessment;
        }

        RiskAssessment combined = new RiskAssessment();
        combined.setTransactionId(amlAssessment.getTransactionId());
        combined.setAmlRiskScore(amlAssessment.getAmlRiskScore());
        combined.setFraudScore(fraudAssessment.getFraudScore());
        combined.setAmlRiskLevel(amlAssessment.getAmlRiskLevel());
        combined.setFraudRiskLevel(fraudAssessment.getFraudRiskLevel());

        // Combine risk factors - null safety
        if (amlAssessment.getRiskFactors() != null) {
            combined.getRiskFactors().addAll(amlAssessment.getRiskFactors());
        }
        if (fraudAssessment.getRiskFactors() != null) {
            combined.getRiskFactors().addAll(fraudAssessment.getRiskFactors());
        }

        return combined;
    }

    /**
     * True when the transaction's payer name is materially different from the
     * merchant's legal / trading name AND from every beneficial owner on file.
     * "Materially different" = Levenshtein distance > THIRD_PARTY_LEVENSHTEIN_THRESHOLD
     * on lowercased, whitespace-collapsed forms.
     */
    private boolean isThirdPartyTransaction(Transaction transaction,
                                            com.posgateway.aml.entity.merchant.Merchant merchant) {
        String payerName = transaction.getMerchantName(); // payer/recipient label on the txn
        if (payerName == null || payerName.isBlank()) {
            // Missing name = unknown, not third-party (avoid false positives on legitimate POS txns)
            return false;
        }
        String payer = normalizeName(payerName);
        if (matchesAny(payer, merchant.getLegalName(), merchant.getTradingName())) {
            return false;
        }
        java.util.List<com.posgateway.aml.entity.merchant.BeneficialOwner> owners =
                beneficialOwnerRepository.findByMerchant_MerchantId(merchant.getMerchantId());
        for (com.posgateway.aml.entity.merchant.BeneficialOwner o : owners) {
            if (matchesAny(payer, o.getFullName())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAny(String normalized, String... candidates) {
        for (String c : candidates) {
            if (c == null) continue;
            String n = normalizeName(c);
            if (n.isEmpty()) continue;
            if (levenshtein(normalized, n) <= THIRD_PARTY_LEVENSHTEIN_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeName(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Iterative DP Levenshtein in O(n*m) time, O(min(n,m)) space. */
    private static int levenshtein(String a, String b) {
        if (a.equals(b)) return 0;
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();
        if (a.length() < b.length()) { String t = a; a = b; b = t; }
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = curr; curr = t;
        }
        return prev[b.length()];
    }

    private String determineDecision(RiskAssessment assessment) {
        // Null safety check
        if (assessment == null) {
            logger.warn("Assessment is null, routing to review");
            return TransactionStatus.UNDER_REVIEW.name();
        }

        // Decision logic based on risk levels using optimized switch for better
        // performance
        // High risk in either AML or Fraud triggers review
        RiskLevel amlLevel = assessment.getAmlRiskLevel();
        RiskLevel fraudLevel = assessment.getFraudRiskLevel();

        // Missing engine output is uncertainty and must not silently approve.
        if (amlLevel == null) {
            logger.debug("AML risk level is null, defaulting to MEDIUM");
            amlLevel = RiskLevel.MEDIUM;
        }
        if (fraudLevel == null) {
            logger.debug("Fraud risk level is null, defaulting to MEDIUM");
            fraudLevel = RiskLevel.MEDIUM;
        }

        // Determine highest risk level (most critical)
        RiskLevel highestRisk = (amlLevel.ordinal() > fraudLevel.ordinal()) ? amlLevel : fraudLevel;

        // Use switch for better performance with enum
        return switch (highestRisk) {
            case HIGH -> TransactionStatus.FLAGGED.name();
            case MEDIUM -> TransactionStatus.UNDER_REVIEW.name();
            case LOW -> TransactionStatus.APPROVED.name();
            default -> TransactionStatus.UNDER_REVIEW.name();
        };
    }

    /** Adapts the legacy request model to the canonical transaction evidence model. */
    private TransactionEntity toTransactionEntity(Transaction transaction) {
        TransactionEntity entity = new TransactionEntity();
        entity.setPanHash(transaction.getAccountNumber());
        entity.setMerchantId(transaction.getMerchantId());
        entity.setPspId(transaction.getPspId());
        entity.setCurrency(transaction.getCurrencyCode());
        entity.setTxnTs(transaction.getTransactionTimestamp());
        entity.setCashTransaction(transaction.isCashTransaction());
        if (transaction.getAmount() != null) {
            try {
                entity.setAmountCents(transaction.getAmount().movePointRight(2).longValueExact());
            } catch (ArithmeticException invalidAmount) {
                logger.warn("Transaction {} amount cannot be represented in cents",
                        transaction.getTransactionId());
            }
        }
        return entity;
    }

    /**
     * Detect third-party usage — the transacting party's name fails to
     * fuzzy-match any registered beneficial owner of the merchant.
     *
     * Returns false (cannot determine) when either:
     *   - the transaction has no comparable name (no merchantName), OR
     *   - the merchant has no registered beneficial owners.
     *
     * The transaction's {@code merchantName} is used as the proxy for the
     * counterparty name; the {@code Transaction} model does not carry a
     * dedicated payer-name field.
     */
    private boolean detectThirdParty(Transaction transaction,
            com.posgateway.aml.entity.merchant.Merchant merchant) {
        if (transaction == null || merchant == null) {
            return false;
        }

        String payerName = transaction.getMerchantName();
        if (payerName == null || payerName.isBlank()) {
            return false;
        }

        java.util.List<com.posgateway.aml.entity.merchant.BeneficialOwner> owners;
        try {
            owners = beneficialOwnerRepository.findByMerchant_MerchantId(merchant.getMerchantId());
        } catch (Exception e) {
            logger.warn("Beneficial-owner lookup failed for merchant {}: {}",
                    merchant.getMerchantId(), e.getMessage());
            return false;
        }
        if (owners == null || owners.isEmpty()) {
            // Fall back to the in-memory relationship if the repo returned nothing.
            owners = merchant.getBeneficialOwners();
        }
        if (owners == null || owners.isEmpty()) {
            return false;
        }

        // Also accept a match against the merchant's own legal/trading name,
        // since legitimate sole-trader payments may name the business directly.
        java.util.List<String> candidates = new java.util.ArrayList<>();
        for (com.posgateway.aml.entity.merchant.BeneficialOwner owner : owners) {
            if (owner != null && owner.getFullName() != null && !owner.getFullName().isBlank()) {
                candidates.add(owner.getFullName());
            }
        }
        if (merchant.getLegalName() != null && !merchant.getLegalName().isBlank()) {
            candidates.add(merchant.getLegalName());
        }
        if (merchant.getTradingName() != null && !merchant.getTradingName().isBlank()) {
            candidates.add(merchant.getTradingName());
        }
        if (candidates.isEmpty()) {
            return false;
        }

        for (String candidate : candidates) {
            if (similarity(payerName, candidate) >= THIRD_PARTY_MATCH_THRESHOLD) {
                return false;
            }
        }
        return true;
    }

    /**
     * Levenshtein-based normalised similarity in [0.0, 1.0]. Inputs are
     * lower-cased, trimmed, and have whitespace runs collapsed before the
     * distance is computed.
     */
    private double similarity(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        String x = a.toLowerCase().trim().replaceAll("\\s+", " ");
        String y = b.toLowerCase().trim().replaceAll("\\s+", " ");
        int maxLen = Math.max(x.length(), y.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = levenshtein(x, y);
        return 1.0 - ((double) distance / maxLen);
    }

}
