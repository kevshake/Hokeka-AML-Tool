package com.posgateway.aml.service;

import com.posgateway.aml.model.RiskAssessment;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.model.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    /**
     * Minimum number of "just-below threshold" transactions from the same
     * account inside the structuring window to flag a structuring pattern.
     */
    private static final long STRUCTURING_REPEAT_COUNT = 3;

    /**
     * Sliding window (hours) over which structuring activity is aggregated.
     */
    private static final int STRUCTURING_WINDOW_HOURS = 24;

    /**
     * Lower bound of the "just-below" range, expressed as a fraction of the
     * CTR threshold. 0.9 means amounts in [0.9 * threshold, threshold).
     */
    private static final BigDecimal JUST_BELOW_FRACTION = new BigDecimal("0.9");

    private final AmlService amlService;
    private final FraudDetectionService fraudDetectionService;
    private final com.posgateway.aml.service.risk.RiskRulesEngine riskRulesEngine;
    private final com.posgateway.aml.repository.MerchantRepository merchantRepository;
    private final com.posgateway.aml.repository.BeneficialOwnerRepository beneficialOwnerRepository;
    private final com.posgateway.aml.service.analytics.LinkAnalysisService linkAnalysisService;
    private final com.posgateway.aml.service.analytics.BehavioralProfilingService behavioralProfilingService;
    private final com.posgateway.aml.repository.TransactionRepository transactionRepository;

    /**
     * CTR (Currency Transaction Report) reporting threshold. In Kenya the CBK
     * threshold is KES 1,000,000. Configurable so other jurisdictions can
     * override via environment.
     */
    private final BigDecimal structuringThreshold;

    /** Levenshtein threshold above which we treat the names as "different person". */
    private static final int THIRD_PARTY_LEVENSHTEIN_THRESHOLD = 3;

    /** Lower bound for structuring detection (KES). Above small-business clean-money territory. */
    private static final java.math.BigDecimal STRUCTURING_LOW = new java.math.BigDecimal("800000");
    /** Upper bound for structuring detection (KES) — sits just below the FRC CTR threshold. */
    private static final java.math.BigDecimal STRUCTURING_HIGH = new java.math.BigDecimal("1000000");

    @Autowired
    public RiskAssessmentService(AmlService amlService,
            FraudDetectionService fraudDetectionService,
            com.posgateway.aml.service.risk.RiskRulesEngine riskRulesEngine,
            com.posgateway.aml.repository.MerchantRepository merchantRepository,
            com.posgateway.aml.repository.BeneficialOwnerRepository beneficialOwnerRepository,
            com.posgateway.aml.service.analytics.LinkAnalysisService linkAnalysisService,
            com.posgateway.aml.service.analytics.BehavioralProfilingService behavioralProfilingService,
            com.posgateway.aml.repository.TransactionRepository transactionRepository,
            @Value("${risk.structuring.threshold:1000000}") BigDecimal structuringThreshold) {
        this.amlService = amlService;
        this.fraudDetectionService = fraudDetectionService;
        this.riskRulesEngine = riskRulesEngine;
        this.merchantRepository = merchantRepository;
        this.beneficialOwnerRepository = beneficialOwnerRepository;
        this.linkAnalysisService = linkAnalysisService;
        this.behavioralProfilingService = behavioralProfilingService;
        this.transactionRepository = transactionRepository;
        this.structuringThreshold = structuringThreshold;
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

            // Structuring (a.k.a. smurfing) detection: STRUCTURING_LOW/HIGH constants define
            // the just-below-CTR band (KES 800,000–1,000,000). Also checks for repeated
            // just-below transactions via DB query over the sliding structuring window.
            boolean isStructuring = detectStructuring(transaction);

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
            logger.warn("Assessment is null, defaulting to APPROVED");
            return TransactionStatus.APPROVED.name();
        }

        // Decision logic based on risk levels using optimized switch for better
        // performance
        // High risk in either AML or Fraud triggers review
        RiskLevel amlLevel = assessment.getAmlRiskLevel();
        RiskLevel fraudLevel = assessment.getFraudRiskLevel();

        // Null safety for risk levels - default to LOW if null
        if (amlLevel == null) {
            logger.debug("AML risk level is null, defaulting to LOW");
            amlLevel = RiskLevel.LOW;
        }
        if (fraudLevel == null) {
            logger.debug("Fraud risk level is null, defaulting to LOW");
            fraudLevel = RiskLevel.LOW;
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

    /**
     * Detect structuring (smurfing) — a single "just-below-CTR-threshold"
     * amount, OR a pattern of repeated just-below transactions from the same
     * account inside the configured sliding window.
     *
     * Just-below range is [threshold * 0.9, threshold) — i.e. amounts that
     * sit deliberately under the CTR reporting trigger.
     */
    private boolean detectStructuring(Transaction transaction) {
        if (transaction == null || transaction.getAmount() == null
                || structuringThreshold == null) {
            return false;
        }

        BigDecimal amount = transaction.getAmount();
        BigDecimal lower = structuringThreshold.multiply(JUST_BELOW_FRACTION);
        boolean amountIsJustBelow = amount.compareTo(lower) >= 0
                && amount.compareTo(structuringThreshold) < 0;

        boolean repeatedJustBelow = false;
        String account = transaction.getAccountNumber();
        if (account != null && !account.isBlank()) {
            try {
                LocalDateTime now = LocalDateTime.now();
                long recentJustBelow = transactionRepository.countByAccountAndAmountRangeAndPeriod(
                        account,
                        lower,
                        structuringThreshold,
                        now.minusHours(STRUCTURING_WINDOW_HOURS),
                        now);
                repeatedJustBelow = recentJustBelow >= STRUCTURING_REPEAT_COUNT;
            } catch (Exception e) {
                logger.warn("Structuring lookup failed for account {}: {}", account, e.getMessage());
            }
        }

        return amountIsJustBelow || repeatedJustBelow;
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

    /**
     * Classic dynamic-programming Levenshtein distance. O(n*m) time, O(n*m)
     * space — acceptable for short name strings.
     */
    private int levenshtein(String s, String t) {
        int n = s.length();
        int m = t.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                dp[i][j] = (s.charAt(i - 1) == t.charAt(j - 1))
                        ? dp[i - 1][j - 1]
                        : 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
        return dp[n][m];
    }
}
