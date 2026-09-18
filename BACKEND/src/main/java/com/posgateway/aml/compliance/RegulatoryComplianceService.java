package com.posgateway.aml.compliance;

import com.posgateway.aml.entity.risk.CountryRiskScore;
import com.posgateway.aml.repository.risk.CountryRiskRepository;
import com.posgateway.aml.service.compliance.RegulatoryExchangeRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Applies Kenya cash-reporting requirements and risk-based FATF country signals.
 *
 * <p>Cash reporting is evaluated against USD 15,000 equivalent using an approved,
 * fresh exchange rate. Missing conversion evidence never produces a false clean
 * result. FATF list membership is not treated as a sanctions match or an automatic
 * country-wide rejection.
 */
@Service
public class RegulatoryComplianceService {

    private static final Logger logger = LoggerFactory.getLogger(RegulatoryComplianceService.class);

    private final RegulatoryExchangeRateService exchangeRateService;
    private final CountryRiskRepository countryRiskRepository;
    private final BigDecimal ctrThresholdUsd;
    private final BigDecimal structuringFloorRatio;
    private final boolean pepEnhancedDueDiligence;

    public RegulatoryComplianceService(
            RegulatoryExchangeRateService exchangeRateService,
            CountryRiskRepository countryRiskRepository,
            @Value("${compliance.kenya.ctr.threshold-usd:15000}") BigDecimal ctrThresholdUsd,
            @Value("${compliance.kenya.structuring.floor-ratio:0.80}") BigDecimal structuringFloorRatio,
            @Value("${compliance.fatf.pep.enhanced-due-diligence:true}") boolean pepEnhancedDueDiligence) {
        this.exchangeRateService = exchangeRateService;
        this.countryRiskRepository = countryRiskRepository;
        this.ctrThresholdUsd = ctrThresholdUsd;
        this.structuringFloorRatio = structuringFloorRatio;
        this.pepEnhancedDueDiligence = pepEnhancedDueDiligence;
    }

    public ComplianceDecision evaluateCompliance(
            BigDecimal amount,
            String currency,
            String countryCode,
            boolean pep,
            long cashTransactionCount24h,
            double mlScore,
            boolean cashTransaction,
            LocalDateTime transactionTime) {
        ComplianceDecision decision = new ComplianceDecision();
        LocalDateTime evaluatedAt = LocalDateTime.now();
        decision.putEvidence("cashTransaction", cashTransaction);
        decision.putEvidence("ctrThresholdUsd", ctrThresholdUsd);
        decision.putEvidence("evaluatedAt", evaluatedAt);

        if (!cashTransaction) {
            decision.putEvidence("ctrEvaluationStatus", "NOT_CASH");
        } else {
            evaluateCashReporting(
                    decision,
                    amount,
                    currency,
                    cashTransactionCount24h,
                    transactionTime != null ? transactionTime : evaluatedAt);
        }

        evaluateCountryRisk(decision, countryCode);

        if (pep && pepEnhancedDueDiligence) {
            decision.setEnhancedDueDiligence(true);
            decision.setDecision("REVIEW");
            decision.addReason("PEP_EDD", "PEP relationship requires enhanced due diligence");
        }

        if (mlScore > 0.9) {
            decision.setDecision("BLOCK");
            decision.addReason("ML_HIGH_RISK", "ML risk score exceeds the configured high-risk threshold");
        } else if (mlScore > 0.7) {
            decision.setDecision("HOLD");
            decision.setStrRequired(true);
            decision.addReason("ML_MEDIUM_RISK", "ML risk score requires manual review");
        }

        if (decision.getDecision() == null) decision.setDecision("ALLOW");
        return decision;
    }

    public ComplianceDecision evaluateCompliance(BigDecimal amount, String currency,
                                                 String countryCode, boolean pep,
                                                 int velocityCount, double mlScore) {
        return evaluateCompliance(
                amount, currency, countryCode, pep, velocityCount, mlScore, false, LocalDateTime.now());
    }

    private void evaluateCashReporting(ComplianceDecision decision, BigDecimal amount,
                                       String currency, long cashTransactionCount24h,
                                       LocalDateTime transactionTime) {
        RegulatoryExchangeRateService.ConversionResult conversion =
                exchangeRateService.convertToUsd(amount, currency, transactionTime);
        if (!conversion.available()) {
            decision.setDecision("HOLD");
            decision.addReason(
                    "CTR_FX_UNAVAILABLE",
                    "Cash-reporting conversion evidence is unavailable: " + conversion.unavailableReason());
            decision.putEvidence("ctrEvaluationStatus", "FX_UNAVAILABLE");
            decision.putEvidence("fxUnavailableReason", conversion.unavailableReason());
            return;
        }

        BigDecimal amountUsd = conversion.amountUsd();
        decision.putEvidence("amountUsd", amountUsd);
        decision.putEvidence("rateToUsd", conversion.rateToUsd());
        decision.putEvidence("rateSource", conversion.source());
        decision.putEvidence("rateEffectiveAt", conversion.effectiveAt());

        if (amountUsd.compareTo(ctrThresholdUsd) >= 0) {
            decision.setCtrRequired(true);
            decision.addReason(
                    "KENYA_CASH_TRANSACTION_REPORT",
                    "Cash transaction meets the USD 15,000 equivalent reporting threshold");
            decision.putEvidence("ctrEvaluationStatus", "REPORTABLE");
            logger.info("Cash transaction marked reportable: amountUsd={}, thresholdUsd={}",
                    amountUsd, ctrThresholdUsd);
            return;
        }

        decision.putEvidence("ctrEvaluationStatus", "BELOW_THRESHOLD");
        BigDecimal structuringFloor = ctrThresholdUsd.multiply(structuringFloorRatio);
        decision.putEvidence("structuringFloorUsd", structuringFloor);
        decision.putEvidence("cashTransactionCount24h", cashTransactionCount24h);
        if (amountUsd.compareTo(structuringFloor) >= 0 && cashTransactionCount24h >= 2) {
            decision.setStrRequired(true);
            decision.setDecision("HOLD");
            decision.addReason(
                    "CASH_STRUCTURING_24H",
                    "Repeated cash transactions fall immediately below the reporting threshold");
        }
    }

    private void evaluateCountryRisk(ComplianceDecision decision, String countryCode) {
        if (countryCode == null || countryCode.isBlank()) return;
        String code = countryCode.trim().toUpperCase(Locale.ROOT);
        if (code.length() != 2) return;

        CountryRiskScore score;
        try {
            score = countryRiskRepository.findByCountryCode(code).orElse(null);
        } catch (RuntimeException lookupFailure) {
            decision.setDecision("HOLD");
            decision.addReason(
                    "COUNTRY_RISK_UNAVAILABLE",
                    "Country-risk reference data could not be evaluated");
            return;
        }
        if (score == null) return;

        decision.putEvidence("countryRiskCode", code);
        decision.putEvidence("countryRiskScore", score.getRiskScore());
        decision.putEvidence("fatfStatus", score.getFatfStatus());
        decision.putEvidence("countryRiskSource", score.getSource());

        if ("BLACKLIST".equalsIgnoreCase(score.getFatfStatus())) {
            decision.setEnhancedDueDiligence(true);
            decision.setDecision("HOLD");
            decision.addReason(
                    "FATF_CALL_FOR_ACTION",
                    "FATF call-for-action jurisdiction requires policy-based EDD or countermeasure review");
        } else if ("GREYLIST".equalsIgnoreCase(score.getFatfStatus())) {
            decision.addReason(
                    "FATF_INCREASED_MONITORING",
                    "FATF increased-monitoring status is a risk signal and not an automatic rejection");
        }
    }

    public BigDecimal getCtrThresholdUsd() {
        return ctrThresholdUsd;
    }

    public static class ComplianceDecision {
        private String decision;
        private boolean ctrRequired;
        private boolean strRequired;
        private boolean enhancedDueDiligence;
        private final Map<String, String> reasons = new LinkedHashMap<>();
        private final Map<String, Object> evidence = new LinkedHashMap<>();

        public String getDecision() { return decision; }

        public void setDecision(String candidate) {
            if (candidate == null) return;
            if (rank(candidate) > rank(decision)) decision = candidate.toUpperCase(Locale.ROOT);
        }

        public boolean isCtrRequired() { return ctrRequired; }
        public void setCtrRequired(boolean ctrRequired) { this.ctrRequired = ctrRequired; }
        public boolean isStrRequired() { return strRequired; }
        public void setStrRequired(boolean strRequired) { this.strRequired = strRequired; }
        public boolean isEnhancedDueDiligence() { return enhancedDueDiligence; }
        public void setEnhancedDueDiligence(boolean enhancedDueDiligence) {
            this.enhancedDueDiligence = enhancedDueDiligence;
        }
        public Map<String, String> getReasons() { return reasons; }
        public void addReason(String code, String description) { reasons.put(code, description); }
        public Map<String, Object> getEvidence() { return evidence; }
        public void putEvidence(String name, Object value) {
            if (value != null) evidence.put(name, value);
        }

        private static int rank(String value) {
            if (value == null) return -1;
            return switch (value.toUpperCase(Locale.ROOT)) {
                case "ALLOW" -> 0;
                case "REVIEW" -> 1;
                case "HOLD" -> 2;
                case "BLOCK" -> 3;
                default -> -1;
            };
        }
    }
}
