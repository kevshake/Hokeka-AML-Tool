package com.posgateway.aml.compliance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Regulatory Compliance Configuration Service.
 * 
 * Ensures compliance with:
 * - CBK (Central Bank of Kenya) AML/CFT Guidelines
 * - FATF (Financial Action Task Force) Recommendations
 * - Kenya Proceeds of Crime and Anti-Money Laundering Act (POCAMLA)
 * 
 * Key Regulations:
 * - CBK Prudential Guidelines on AML/CFT (CBK/PG/08)
 * - FATF Recommendations 2012 (as amended)
 * - Kenya AML Act 2009 (as amended 2017)
 */
@Service
public class RegulatoryComplianceService {

    private static final Logger logger = LoggerFactory.getLogger(RegulatoryComplianceService.class);

    // =========================================================================
    // CBK THRESHOLDS (Kenya Shillings - KES)
    // =========================================================================

    @Value("${compliance.cbk.ctr.threshold.kes:1000000}")
    private long cbkCtrThresholdKes; // KES 1,000,000 (~$7,500 USD) CTR threshold

    @Value("${compliance.cbk.str.threshold.kes:500000}")
    private long cbkStrThresholdKes; // KES 500,000 STR review threshold

    @Value("${compliance.cbk.structuring.threshold.kes:900000}")
    private long cbkStructuringThresholdKes; // Just under CTR for structuring detection

    // =========================================================================
    // FATF THRESHOLDS (USD)
    // =========================================================================

    @Value("${compliance.fatf.wire.threshold.usd:3000}")
    private long fatfWireThresholdUsd; // FATF Rec 16 - Wire transfer threshold

    @Value("${compliance.fatf.pep.enhanced.due.diligence:true}")
    private boolean fatfPepEnhancedDueDiligence;

    // =========================================================================
    // HIGH-RISK JURISDICTIONS (FATF Grey/Black Lists)
    // =========================================================================

    // FATF Black List (Call for Action) - As of 2024
    private static final Set<String> FATF_BLACKLIST = Set.of(
            "KP", // North Korea
            "IR", // Iran
            "MM" // Myanmar
    );

    // FATF Grey List (Increased Monitoring) - As of 2024
    private static final Set<String> FATF_GREYLIST = Set.of(
            "BG", "BF", "CM", "HR", "CD", "HT", "KE", "ML", "MZ",
            "NG", "PH", "SN", "ZA", "SS", "SY", "TZ", "TR", "UG",
            "AE", "VN", "YE");

    // CBK High-Risk Countries (includes FATF + regional concerns)
    private static final Set<String> CBK_HIGH_RISK = Set.of(
            "KP", "IR", "MM", "SY", "YE", "SS", "SO", "LY", "AF");

    // =========================================================================
    // COMPLIANCE CHECKS
    // =========================================================================

    /**
     * Check if transaction requires CTR (Currency Transaction Report).
     * CBK: KES 1,000,000+ or equivalent
     */
    public boolean requiresCtr(BigDecimal amountKes) {
        boolean required = amountKes.compareTo(BigDecimal.valueOf(cbkCtrThresholdKes)) >= 0;
        if (required) {
            logger.info("CTR required: Amount KES {} exceeds CBK threshold of KES {}",
                    amountKes, cbkCtrThresholdKes);
        }
        return required;
    }

    /**
     * Check if transaction triggers STR (Suspicious Transaction Report) review.
     */
    public boolean requiresStrReview(BigDecimal amountKes, String countryCode,
            int velocityCount, double mlScore) {
        List<String> reasons = new ArrayList<>();

        // Amount-based
        if (amountKes.compareTo(BigDecimal.valueOf(cbkStrThresholdKes)) >= 0) {
            reasons.add("High value transaction");
        }

        // Structuring detection (multiple transactions just under CTR)
        if (amountKes.compareTo(BigDecimal.valueOf(cbkStructuringThresholdKes)) >= 0 &&
                amountKes.compareTo(BigDecimal.valueOf(cbkCtrThresholdKes)) < 0 &&
                velocityCount >= 2) {
            reasons.add("Potential structuring detected");
        }

        // High-risk jurisdiction
        if (isHighRiskJurisdiction(countryCode)) {
            reasons.add("High-risk jurisdiction: " + countryCode);
        }

        // High ML risk score
        if (mlScore > 0.7) {
            reasons.add("High ML risk score: " + String.format("%.2f", mlScore));
        }

        if (!reasons.isEmpty()) {
            logger.info("STR review triggered for: {}", String.join(", ", reasons));
            return true;
        }
        return false;
    }

    /**
     * Check if country is on FATF blacklist (immediate block).
     */
    public boolean isFatfBlacklisted(String countryCode) {
        return FATF_BLACKLIST.contains(countryCode.toUpperCase());
    }

    /**
     * Check if country is on FATF greylist (enhanced due diligence).
     */
    public boolean isFatfGreylisted(String countryCode) {
        return FATF_GREYLIST.contains(countryCode.toUpperCase());
    }

    /**
     * Check if country is high-risk per CBK guidelines.
     */
    public boolean isCbkHighRisk(String countryCode) {
        return CBK_HIGH_RISK.contains(countryCode.toUpperCase());
    }

    /**
     * Check if any high-risk jurisdiction flag applies.
     */
    public boolean isHighRiskJurisdiction(String countryCode) {
        if (countryCode == null)
            return false;
        String code = countryCode.toUpperCase();
        return FATF_BLACKLIST.contains(code) ||
                FATF_GREYLIST.contains(code) ||
                CBK_HIGH_RISK.contains(code);
    }

    /**
     * Get compliance decision for a transaction.
     */
    public ComplianceDecision evaluateCompliance(BigDecimal amountKes, String currency,
            String countryCode, boolean isPep,
            int velocityCount, double mlScore) {
        ComplianceDecision decision = new ComplianceDecision();

        // 1. FATF Blacklist - Immediate block
        if (isFatfBlacklisted(countryCode)) {
            decision.setDecision("BLOCK");
            decision.addReason("FATF_BLACKLIST", "Transaction involves FATF blacklisted country: " + countryCode);
            decision.setStrRequired(true);
            return decision;
        }

        // 2. CBK High-Risk - Block
        if (isCbkHighRisk(countryCode)) {
            decision.setDecision("BLOCK");
            decision.addReason("CBK_HIGH_RISK", "Transaction involves CBK high-risk country: " + countryCode);
            decision.setStrRequired(true);
            return decision;
        }

        // 3. CTR Threshold
        if (requiresCtr(amountKes)) {
            decision.setCtrRequired(true);
            decision.addReason("CTR_THRESHOLD", "Amount exceeds CBK CTR threshold");
        }

        // 4. FATF Greylist - Enhanced Due Diligence
        if (isFatfGreylisted(countryCode)) {
            decision.setEnhancedDueDiligence(true);
            decision.addReason("FATF_GREYLIST", "Enhanced due diligence required for FATF greylist country");
        }

        // 5. PEP Check
        if (isPep && fatfPepEnhancedDueDiligence) {
            decision.setEnhancedDueDiligence(true);
            decision.addReason("PEP_EDD", "PEP transaction requires enhanced due diligence per FATF Rec 12");
        }

        // 6. Structuring Detection
        if (amountKes.compareTo(BigDecimal.valueOf(cbkStructuringThresholdKes)) >= 0 &&
                amountKes.compareTo(BigDecimal.valueOf(cbkCtrThresholdKes)) < 0 &&
                velocityCount >= 2) {
            decision.setStrRequired(true);
            decision.setDecision("HOLD");
            decision.addReason("STRUCTURING", "Potential structuring: multiple transactions just under CTR threshold");
        }

        // 7. ML Score Threshold
        if (mlScore > 0.9) {
            decision.setDecision("BLOCK");
            decision.addReason("ML_HIGH_RISK", "ML risk score exceeds 0.9 threshold");
        } else if (mlScore > 0.7) {
            decision.setDecision("HOLD");
            decision.setStrRequired(true);
            decision.addReason("ML_MEDIUM_RISK", "ML risk score requires manual review");
        }

        // Default to ALLOW if no issues
        if (decision.getDecision() == null) {
            decision.setDecision("ALLOW");
        }

        return decision;
    }

    /**
     * Get configured CBK CTR threshold.
     */
    public long getCbkCtrThresholdKes() {
        return cbkCtrThresholdKes;
    }

    /**
     * Get FATF wire transfer threshold.
     */
    public long getFatfWireThresholdUsd() {
        return fatfWireThresholdUsd;
    }

    /**
     * Compliance Decision Result.
     */
    public static class ComplianceDecision {
        private String decision;
        private boolean ctrRequired = false;
        private boolean strRequired = false;
        private boolean enhancedDueDiligence = false;
        private final Map<String, String> reasons = new LinkedHashMap<>();

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            // Only upgrade decisions (ALLOW < HOLD < BLOCK)
            if (this.decision == null ||
                    ("HOLD".equals(decision) && "ALLOW".equals(this.decision)) ||
                    "BLOCK".equals(decision)) {
                this.decision = decision;
            }
        }

        public boolean isCtrRequired() {
            return ctrRequired;
        }

        public void setCtrRequired(boolean ctrRequired) {
            this.ctrRequired = ctrRequired;
        }

        public boolean isStrRequired() {
            return strRequired;
        }

        public void setStrRequired(boolean strRequired) {
            this.strRequired = strRequired;
        }

        public boolean isEnhancedDueDiligence() {
            return enhancedDueDiligence;
        }

        public void setEnhancedDueDiligence(boolean edd) {
            this.enhancedDueDiligence = edd;
        }

        public Map<String, String> getReasons() {
            return reasons;
        }

        public void addReason(String code, String description) {
            reasons.put(code, description);
        }

        @Override
        public String toString() {
            return String.format("ComplianceDecision[decision=%s, CTR=%s, STR=%s, EDD=%s, reasons=%d]",
                    decision, ctrRequired, strRequired, enhancedDueDiligence, reasons.size());
        }
    }
}
