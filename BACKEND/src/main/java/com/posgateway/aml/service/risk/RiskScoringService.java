
package com.posgateway.aml.service.risk;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.service.rules.RulesExecutionService;
import com.posgateway.aml.rules.RuleEvaluationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Service for calculating Risk Scores based on Flagright methodology.
 * Includes:
 * - KYC Risk Score (KRS)
 * - Transaction Risk Score (TRS)
 * - Customer Risk Assessment (CRA)
 */
@Service
public class RiskScoringService {

    private final MeterRegistry meterRegistry;
    private final RulesExecutionService rulesExecutionService;

    @Autowired
    public RiskScoringService(MeterRegistry meterRegistry, RulesExecutionService rulesExecutionService) {
        this.meterRegistry = meterRegistry;
        this.rulesExecutionService = rulesExecutionService;
    }

    // --- Weights Configuration (Can be moved to DB later) ---
    // KRS Weights
    private static final double W_COUNTRY_RESIDENCE = 0.5;
    private static final double W_NATIONALITY = 0.3; // Using Business Type/MCC as proxy for "Nationality" if specific field missing
    private static final double W_AGE = 0.2; // Using Business Age

    // TRS Weights
    private static final double W_ORIGIN_COUNTRY = 0.3;
    private static final double W_DEST_COUNTRY = 0.3;
    private static final double W_AMOUNT = 0.4;

    // --- Risk Reference Tables (Simplification) ---
    private static final Map<String, Double> COUNTRY_RISK = new HashMap<>();
    static {
        COUNTRY_RISK.put("US", 10.0);
        COUNTRY_RISK.put("GB", 10.0);
        COUNTRY_RISK.put("KE", 30.0);
        COUNTRY_RISK.put("AE", 54.0); // Detailed in example
        COUNTRY_RISK.put("IR", 100.0);
        COUNTRY_RISK.put("KP", 100.0);
        COUNTRY_RISK.put("UNKNOWN", 50.0);
    }
    
    private static final Map<String, Double> MCC_RISK = new HashMap<>(); // Proxy for Industry Risk
    static {
        MCC_RISK.put("5411", 10.0); // Grocery
        MCC_RISK.put("7995", 90.0); // Gambling
        MCC_RISK.put("5999", 50.0); // Misc
    }

    /**
     * Calculate KYC Risk Score (KRS) for a Merchant.
     * Formula: Weighted Average of risk factors.
     */
    public Double calculateKrs(Merchant merchant) {
        if (merchant == null) return 50.0;

        // 1. Country Risk (Residence/Registration)
        double scoreCountry = getCountryRisk(merchant.getCountry());

        // 2. Business Type/MCC Risk (Proxy for "Nationality/Nature")
        double scoreMcc = MCC_RISK.getOrDefault(merchant.getMcc(), 50.0);

        // 3. Age Risk (New vs Old)
        double scoreAge = merchant.isNew() ? 60.0 : 20.0; // Newer is riskier

        // Calculation
        double weightedSum = (scoreCountry * W_COUNTRY_RESIDENCE) + 
                             (scoreMcc * W_NATIONALITY) + 
                             (scoreAge * W_AGE);
        
        double sumWeights = W_COUNTRY_RESIDENCE + W_NATIONALITY + W_AGE;
        
        double krs = weightedSum / sumWeights;

        // Record Metric
        meterRegistry.gauge("aml.risk.krs", Tags.of("merchant_id", merchant.getMerchantId().toString()), krs);

        return krs;
    }

    /**
     * Calculate Transaction Risk Score (TRS).
     * Formula: Weighted Average of transaction factors.
     */
    public Double calculateTrs(String originCountry, String destCountry, BigDecimal amount) {
        // 1. Origin Risk
        double scoreOrigin = getCountryRisk(originCountry);
        
        // 2. Dest Risk
        double scoreDest = getCountryRisk(destCountry);
        
        // 3. Amount Risk (Normalized)
        double scoreAmount = calculateAmountRisk(amount);

        // Calculation
        double weightedSum = (scoreOrigin * W_ORIGIN_COUNTRY) + 
                             (scoreDest * W_DEST_COUNTRY) + 
                             (scoreAmount * W_AMOUNT);
        
        double sumWeights = W_ORIGIN_COUNTRY + W_DEST_COUNTRY + W_AMOUNT;
        
        double trs = weightedSum / sumWeights;

        // Record Metric
        meterRegistry.gauge("aml.risk.trs", Tags.of("origin", originCountry, "dest", destCountry), trs);
        
        return trs; 
    }

    /**
     * Update Customer Risk Assessment (CRA).
     * Formula: CRA[i] = Avg( CRA[i-1] + TRS[i] )
     * Uses exponential moving average logic or simple average as per instruction.
     */
    public Double updateCra(Double currentCra, Double newTrs) {
        if (currentCra == null || currentCra == 0.0) {
            return newTrs;
        }
        // Formula per prompt image: avg(CRA[i-1] + TRS[i]) which implies (Prev + New) / 2
        double newCra = (currentCra + newTrs) / 2.0;

        // Record Metric
        meterRegistry.gauge("aml.risk.cra", Tags.of("type", "rolling_avg"), newCra);

        return newCra;
    }

    public Map<String, Object> calculateOverallRisk(String txnId, Map<String, Object> features, Map<String, Object> riskDetails) {
        // 1. Calculate KRS (assuming merchant_id is in features)
        // This part would typically involve fetching the merchant and calling calculateKrs(merchant)
        // For this example, we'll use a placeholder or assume KRS is passed in features/riskDetails
        double krsScore = ((Number) features.getOrDefault("krs_score", 50.0)).doubleValue();

        // 2. Calculate Base TRS (using existing method or a new one based on features)
        // This is a simplified call, actual implementation would extract origin, dest, amount from features
        double baseTrs = calculateTrs(
            (String) features.getOrDefault("origin_country", "UNKNOWN"),
            (String) features.getOrDefault("destination_country", "UNKNOWN"),
            new BigDecimal(features.getOrDefault("amount", 0.0).toString())
        );

        // 3. Dynamic Rules & Legacy Rules Check
        RuleEvaluationResult ruleResult = rulesExecutionService.evaluateRules(Long.valueOf(txnId), features, ((Number) riskDetails.getOrDefault("mlScore", 0.0)).doubleValue());
        
        // 4. Combine Scores
        double trsScore = calculateTrs(features, ruleResult);
        double craScore = calculateCra(features); // Placeholder for advanced CRA
        double mlScore = ((Number) riskDetails.getOrDefault("mlScore", 0.0)).doubleValue();
        
        // Final Score Calculation (Weighted)
        double finalScore = (krsScore * 0.3) + (trsScore * 0.4) + (craScore * 0.3);
        
        // Apply Rules Impact (e.g. if rule says BLOCK, force score to 100 ?)
        if ("BLOCK".equals(ruleResult.getDecision())) {
             finalScore = 100.0;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("finalScore", finalScore);
        result.put("krsScore", krsScore);
        result.put("trsScore", trsScore);
        result.put("craScore", craScore);
        result.put("mlScore", mlScore);
        // Pass down decision/reasons from rules
        result.put("ruleDecision", ruleResult.getDecision());
        result.put("ruleReasons", ruleResult.getReasons());
        
        return result;
    }

    private double getCountryRisk(String code) {
        return COUNTRY_RISK.getOrDefault(code, 50.0);
    }

    private double calculateAmountRisk(BigDecimal amount) {
        if (amount == null) return 0.0;
        double val = amount.doubleValue();
        if (val < 1000) return 10.0;
        if (val < 5000) return 30.0;
        if (val < 10000) return 50.0;
        if (val < 50000) return 80.0;
        return 100.0;
    }

    private double calculateTrs(Map<String, Object> features, RuleEvaluationResult ruleResult) {
        double score = 0.0;
        
        // Base: Rule triggers (each trigger adds 20 points, cap at 100)
        score += ruleResult.getTriggeredRules().size() * 20.0;
        
        // Amt > 10000 adds 30
        double amount = Double.parseDouble(features.getOrDefault("amount", "0").toString());
        if (amount > 10000) score += 30;
        
        // High risk country - NOTE: countryRepository is not defined in this file. This line will cause a compilation error.
        // Assuming a placeholder or that countryRepository would be injected if this method were fully implemented.
        // if (countryRepository.existsByCountryCode(country)) score += 40;
        String country = (String) features.getOrDefault("country_code", "US");
        if (COUNTRY_RISK.getOrDefault(country, 0.0) > 50.0) score += 40; // Using existing COUNTRY_RISK map as a proxy

        return Math.min(100.0, score);
    }

    // Placeholder for a more advanced CRA calculation based on features
    private double calculateCra(Map<String, Object> features) {
        // This would involve fetching historical data, applying moving averages, etc.
        // For now, return a default or a value derived from features.
        return ((Number) features.getOrDefault("current_cra", 50.0)).doubleValue();
    }
}

