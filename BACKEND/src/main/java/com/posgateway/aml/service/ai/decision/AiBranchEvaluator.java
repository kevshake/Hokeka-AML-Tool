package com.posgateway.aml.service.ai.decision;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Evaluates provisional per-PSP bands using raw probabilities (not weighted score indices alone).
 * Threshold symbols are placeholders until labeled tuning (see DESIGN.md §6–§7).
 */
@Service
public class AiBranchEvaluator {

    public record ThresholdSet(
            String version,
            boolean provisional,
            Map<String, Double> values
    ) {}

    public record EvaluationResult(
            AiBranch branch,
            ThresholdSet thresholds,
            Map<String, Object> diagnostics
    ) {}

    private final AiBandsService bandsService;

    public AiBranchEvaluator(AiBandsService bandsService) {
        this.bandsService = bandsService;
    }

    public EvaluationResult evaluate(AiDecisionPoint decisionPoint,
                                     Long pspId,
                                     Map<String, JsonNode> answers,
                                     AiDecisionContext context) {
        ThresholdSet thresholds = bandsService.thresholdsFor(pspId, decisionPoint);
        return switch (decisionPoint) {
            case DP1_TM_ALERT_TRIAGE -> evaluateDp1(answers, thresholds, context);
            case DP2_SCREENING_MATCH -> evaluateDp2(answers, thresholds);
            case DP3_CASE_TRIAGE -> evaluateDp3(answers, thresholds);
            case DP4_CUSTOMER_RISK -> evaluateDp4(answers, thresholds, context);
            case DP5_SAR_NARRATIVE -> evaluateDp5(answers, thresholds);
        };
    }

    private EvaluationResult evaluateDp1(Map<String, JsonNode> answers,
                                         ThresholdSet thresholds,
                                         AiDecisionContext context) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        double laundering = noul(answers, "laundering_suspicion");
        double highOrSevere = bucketMass(answers, "activity_risk", "3", "4");
        String owningQueue = choice(answers, "owning_queue");
        diagnostics.put("laundering_suspicion", laundering);
        diagnostics.put("activity_risk_high_or_severe_mass", highOrSevere);
        diagnostics.put("owning_queue", owningQueue);

        double tUp = threshold(thresholds, "t_up", 0.50);
        double tUp2 = threshold(thresholds, "t_up2", 0.50);
        if (laundering >= tUp || highOrSevere >= tUp2 || "mlro_direct".equals(owningQueue)) {
            return new EvaluationResult(AiBranch.ESCALATE_UP, thresholds, diagnostics);
        }

        double tLow = threshold(thresholds, "t_low", 0.02);
        double tPc = threshold(thresholds, "t_pc", 0.80);
        double tLr = threshold(thresholds, "t_lr", 0.90);
        double profileConsistent = noul(answers, "profile_consistent");
        double lowRiskMass = bucketMass(answers, "activity_risk", "0", "1");
        diagnostics.put("profile_consistent", profileConsistent);
        diagnostics.put("activity_risk_low_mass", lowRiskMass);

        if (laundering < tLow
                && profileConsistent >= tPc
                && lowRiskMass >= tLr
                && !hasHardRegulatoryFlag(context)) {
            return new EvaluationResult(AiBranch.LOW_RISK_CANDIDATE, thresholds, diagnostics);
        }
        return new EvaluationResult(AiBranch.DEFAULT, thresholds, diagnostics);
    }

    private EvaluationResult evaluateDp2(Map<String, JsonNode> answers, ThresholdSet thresholds) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        double sameEntity = noul(answers, "same_entity");
        String matchBasis = choice(answers, "match_basis");
        diagnostics.put("same_entity", sameEntity);
        diagnostics.put("match_basis", matchBasis);

        double tMatch = threshold(thresholds, "t_match", 0.50);
        if (sameEntity >= tMatch || "identifier_confirmed".equals(matchBasis)) {
            return new EvaluationResult(AiBranch.ESCALATE_UP, thresholds, diagnostics);
        }
        double tFp = threshold(thresholds, "t_fp", 0.10);
        if (sameEntity < tFp && "contradicted".equals(matchBasis)) {
            return new EvaluationResult(AiBranch.LIKELY_FALSE_POSITIVE, thresholds, diagnostics);
        }
        return new EvaluationResult(AiBranch.DEFAULT, thresholds, diagnostics);
    }

    private EvaluationResult evaluateDp3(Map<String, JsonNode> answers, ThresholdSet thresholds) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        double strGrounds = noul(answers, "str_grounds");
        String nextStep = choice(answers, "next_step");
        diagnostics.put("str_grounds", strGrounds);
        diagnostics.put("next_step", nextStep);

        double tStr = threshold(thresholds, "t_str", 0.50);
        if (strGrounds >= tStr) {
            return new EvaluationResult(AiBranch.ESCALATE_UP, thresholds, diagnostics);
        }
        double tClose = threshold(thresholds, "t_close", 0.10);
        if ("close_no_suspicion".equals(nextStep) && strGrounds < tClose) {
            return new EvaluationResult(AiBranch.PROPOSE_CLOSURE, thresholds, diagnostics);
        }
        return new EvaluationResult(AiBranch.DEFAULT, thresholds, diagnostics);
    }

    private EvaluationResult evaluateDp4(Map<String, JsonNode> answers,
                                         ThresholdSet thresholds,
                                         AiDecisionContext context) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        double eddRequired = noul(answers, "edd_required");
        double highTierMass = bucketMass(answers, "risk_tier", "2", "3");
        diagnostics.put("edd_required", eddRequired);
        diagnostics.put("risk_tier_high_or_unacceptable_mass", highTierMass);

        double tTier = threshold(thresholds, "t_tier", 0.50);
        double tEdd = threshold(thresholds, "t_edd", 0.50);
        int deterministicTier = deterministicTier(context);
        int aiTier = dominantScoreBucket(answers, "risk_tier");
        diagnostics.put("deterministic_risk_tier", deterministicTier);
        diagnostics.put("ai_risk_tier_bucket", aiTier);

        if ((aiTier > deterministicTier && highTierMass >= tTier) || eddRequired >= tEdd) {
            return new EvaluationResult(AiBranch.RAISE_RISK_TIER, thresholds, diagnostics);
        }
        return new EvaluationResult(AiBranch.DEFAULT, thresholds, diagnostics);
    }

    private EvaluationResult evaluateDp5(Map<String, JsonNode> answers, ThresholdSet thresholds) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        double supported = noul(answers, "narrative_supported");
        diagnostics.put("narrative_supported", supported);
        double tSupport = threshold(thresholds, "t_support", 0.80);
        if (supported >= tSupport) {
            return new EvaluationResult(AiBranch.DEFAULT, thresholds, diagnostics);
        }
        return new EvaluationResult(AiBranch.ESCALATE_HUMAN, thresholds, diagnostics);
    }

    static double noul(Map<String, JsonNode> answers, String question) {
        return answers.get(question).path("noul").asDouble(0.0);
    }

    static String choice(Map<String, JsonNode> answers, String question) {
        return answers.get(question).path("choice").asText("");
    }

    static double bucketMass(Map<String, JsonNode> answers, String question, String... bucketKeys) {
        JsonNode probs = answers.get(question).path("probabilities");
        double sum = 0.0;
        for (String key : bucketKeys) {
            sum += probs.path(key).asDouble(0.0);
        }
        return sum;
    }

    static int dominantScoreBucket(Map<String, JsonNode> answers, String question) {
        JsonNode probs = answers.get(question).path("probabilities");
        int best = 0;
        double bestVal = -1.0;
        var fields = probs.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            double val = entry.getValue().asDouble(0.0);
            if (val > bestVal) {
                bestVal = val;
                best = Integer.parseInt(entry.getKey());
            }
        }
        return best;
    }

    static double threshold(ThresholdSet thresholds, String key, double placeholder) {
        Double configured = thresholds.values().get(key);
        return configured != null ? configured : placeholder;
    }

    private static boolean hasHardRegulatoryFlag(AiDecisionContext context) {
        Map<String, Object> features = context.getFeatures();
        if (features == null) {
            return false;
        }
        Object sanctions = features.get("sanctions_screening");
        if ("MATCH".equals(sanctions) || "POTENTIAL_MATCH".equals(sanctions)) {
            return true;
        }
        if (Boolean.TRUE.equals(features.get("sar_required_flag"))
                || Boolean.TRUE.equals(features.get("ctr_required_flag"))
                || Boolean.TRUE.equals(features.get("cross_psp_flag"))) {
            return true;
        }
        String baseline = context.getBaselineDecision();
        return baseline != null && switch (baseline.toUpperCase()) {
            case "BLOCK", "HOLD", "SANCTIONS_MATCH" -> true;
            default -> false;
        };
    }

    private static int deterministicTier(AiDecisionContext context) {
        Map<String, Object> features = context.getFeatures();
        if (features == null) {
            return 0;
        }
        Object tier = features.get("deterministic_risk_tier");
        if (tier instanceof Number n) {
            return n.intValue();
        }
        Object level = features.get("cra_level");
        if (level == null) {
            return 0;
        }
        return switch (String.valueOf(level).toUpperCase()) {
            case "LOW" -> 0;
            case "MEDIUM" -> 1;
            case "HIGH" -> 2;
            case "CRITICAL", "UNACCEPTABLE" -> 3;
            default -> 0;
        };
    }
}
