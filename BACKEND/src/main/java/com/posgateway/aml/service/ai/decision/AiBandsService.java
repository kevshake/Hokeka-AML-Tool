package com.posgateway.aml.service.ai.decision;

import com.posgateway.aml.config.ai.AiDecisionProperties;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * Loads provisional band thresholds per PSP and decision point.
 * Values are placeholders until labeled tuning; see DESIGN.md §6–§7.
 */
@Service
public class AiBandsService {

    private final AiDecisionProperties properties;

    public AiBandsService(AiDecisionProperties properties) {
        this.properties = properties;
    }

    public AiBranchEvaluator.ThresholdSet thresholdsFor(Long pspId, AiDecisionPoint decisionPoint) {
        Map<String, Double> values = new java.util.LinkedHashMap<>(defaultPlaceholders(decisionPoint));
        // Per-PSP overrides from config can be added here; DB-backed overrides via ai_bands in a follow-up.
        return new AiBranchEvaluator.ThresholdSet(
                properties.getBandsVersion(),
                true,
                values);
    }

    private static Map<String, Double> defaultPlaceholders(AiDecisionPoint decisionPoint) {
        Map<AiDecisionPoint, Map<String, Double>> defaults = new EnumMap<>(AiDecisionPoint.class);
        defaults.put(AiDecisionPoint.DP1_TM_ALERT_TRIAGE, Map.of(
                "t_up", 0.50,
                "t_up2", 0.50,
                "t_low", 0.02,
                "t_pc", 0.80,
                "t_lr", 0.90
        ));
        defaults.put(AiDecisionPoint.DP2_SCREENING_MATCH, Map.of(
                "t_match", 0.50,
                "t_fp", 0.10
        ));
        defaults.put(AiDecisionPoint.DP3_CASE_TRIAGE, Map.of(
                "t_str", 0.50,
                "t_close", 0.10
        ));
        defaults.put(AiDecisionPoint.DP4_CUSTOMER_RISK, Map.of(
                "t_tier", 0.50,
                "t_edd", 0.50
        ));
        defaults.put(AiDecisionPoint.DP5_SAR_NARRATIVE, Map.of(
                "t_support", 0.80
        ));
        return new java.util.LinkedHashMap<>(defaults.getOrDefault(decisionPoint, Map.of()));
    }
}
