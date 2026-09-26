package com.posgateway.aml.service.jev;

import org.springframework.stereotype.Component;

/**
 * Structural tighten-only authority: Jev may raise priority/route/alert/EDD recommendations,
 * but must never close, dismiss, downgrade, lower tiers, or file STR/SAR.
 */
@Component
public class JevTightenOnlyAuthority {

    public enum SuggestedMutation {
        NONE,
        ROUTE_AML_INVESTIGATIONS,
        ROUTE_MLRO,
        ROUTE_SANCTIONS,
        ROUTE_FRAUD,
        RAISE_PRIORITY,
        RECOMMEND_EDD,
        RAISE_RISK_TIER,
        PROPOSE_CLOSURE,
        LIKELY_FP_REVIEW
    }

    public record AuthorityResult(
            String finalDecision,
            SuggestedMutation mutation,
            boolean wouldApply,
            boolean allowed
    ) {}

    public AuthorityResult apply(String baselineDecision,
                                 JevBranch branch,
                                 JevDecisionContext context,
                                 boolean shadowMode,
                                 boolean promoted) {
        String baseline = baselineDecision != null ? baselineDecision : "REVIEW";
        SuggestedMutation mutation = mutationFor(branch);
        boolean allowed = isAllowedMutation(mutation, baseline, context);
        boolean active = !shadowMode && promoted && allowed;
        String finalDecision = baseline;

        if (active && mutation != SuggestedMutation.NONE) {
            finalDecision = tightenDecision(baseline, branch);
        }

        return new AuthorityResult(finalDecision, mutation, allowed && mutation != SuggestedMutation.NONE, allowed);
    }

    static SuggestedMutation mutationFor(JevBranch branch) {
        return switch (branch) {
            case ESCALATE_UP -> SuggestedMutation.RAISE_PRIORITY;
            case LOW_RISK_CANDIDATE -> SuggestedMutation.NONE;
            case LIKELY_FALSE_POSITIVE -> SuggestedMutation.LIKELY_FP_REVIEW;
            case PROPOSE_CLOSURE -> SuggestedMutation.PROPOSE_CLOSURE;
            case RAISE_RISK_TIER -> SuggestedMutation.RAISE_RISK_TIER;
            case DEFAULT, ESCALATE_HUMAN -> SuggestedMutation.NONE;
        };
    }

    static boolean isAllowedMutation(SuggestedMutation mutation, String baseline, JevDecisionContext context) {
        if (mutation == SuggestedMutation.NONE) {
            return false;
        }
        if (mutation == SuggestedMutation.PROPOSE_CLOSURE) {
            // Never auto-close; only allows analyst proposal path.
            return !isHardBaseline(baseline);
        }
        if (mutation == SuggestedMutation.LIKELY_FP_REVIEW) {
            // Never auto-dismiss screening hits.
            return false;
        }
        if (mutation == SuggestedMutation.RAISE_RISK_TIER || mutation == SuggestedMutation.RECOMMEND_EDD) {
            return true;
        }
        if (mutation == SuggestedMutation.RAISE_PRIORITY) {
            return !wouldDowngrade(baseline, "HOLD");
        }
        return true;
    }

    static String tightenDecision(String baseline, JevBranch branch) {
        int baselineSeverity = severity(baseline);
        int target = switch (branch) {
            case ESCALATE_UP -> Math.max(baselineSeverity, severity("ALERT"));
            case RAISE_RISK_TIER -> baselineSeverity;
            default -> baselineSeverity;
        };
        return actionForSeverity(Math.max(baselineSeverity, target));
    }

    static boolean wouldDowngrade(String baseline, String proposed) {
        return severity(proposed) < severity(baseline);
    }

    static boolean isHardBaseline(String baseline) {
        if (baseline == null) {
            return false;
        }
        return switch (baseline.toUpperCase()) {
            case "BLOCK", "HOLD", "SANCTIONS_MATCH" -> true;
            default -> false;
        };
    }

    static int severity(String action) {
        if (action == null) {
            return 0;
        }
        return switch (action.toUpperCase()) {
            case "ALLOW", "APPROVE" -> 0;
            case "ALERT", "REVIEW" -> 1;
            case "HOLD", "ESCALATE" -> 2;
            case "BLOCK", "DECLINE", "SANCTIONS_MATCH" -> 3;
            default -> 1;
        };
    }

    static String actionForSeverity(int severity) {
        return switch (severity) {
            case 0 -> "ALLOW";
            case 1 -> "ALERT";
            case 2 -> "HOLD";
            default -> "BLOCK";
        };
    }
}
