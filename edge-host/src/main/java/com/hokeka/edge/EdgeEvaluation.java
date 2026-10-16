package com.hokeka.edge;

import java.util.List;

/**
 * Response of {@code POST /edge/evaluate}: the decision plus the identifiers the PSP needs for the
 * cloud dual-post.
 *
 * <p>{@code edgeAssessmentId} is a UUID minted for every evaluation. The PSP passes it as
 * {@code edgeAssessmentId} on {@code POST /api/v1/transactions/ingest}; the edge ships a decision
 * envelope under the same id to {@code POST /api/v1/edge/assessments}, and the Control Plane links
 * both (see docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md). The first four fields keep the original
 * response shape, so existing integrations keep parsing it.
 *
 * @param action                  decision
 * @param score                   accumulated rule score
 * @param triggeredRuleIds        rules that fired
 * @param reasons                 human-readable reasons
 * @param triggeredRuleVersionIds rule version ids that fired (when the bundle carries them)
 * @param edgeAssessmentId        UUID correlating this evaluation with the cloud ingest
 * @param ruleBundleVersion       active rule bundle version (-1 before the first bundle)
 * @param ruleBundleHash          active rule bundle manifest hash ("" before the first bundle)
 */
public record EdgeEvaluation(EdgeRuleInterpreter.Action action,
                             int score,
                             List<Long> triggeredRuleIds,
                             List<String> reasons,
                             List<Long> triggeredRuleVersionIds,
                             String edgeAssessmentId,
                             long ruleBundleVersion,
                             String ruleBundleHash) {

    /** Combines a decision with its correlation id and the bundle it was evaluated against. */
    static EdgeEvaluation of(EdgeRuleInterpreter.Decision decision, String edgeAssessmentId,
                             long ruleBundleVersion, String ruleBundleHash) {
        return new EdgeEvaluation(decision.action(), decision.score(),
                decision.triggeredRuleIds() == null ? List.of() : decision.triggeredRuleIds(),
                decision.reasons() == null ? List.of() : decision.reasons(),
                decision.triggeredRuleVersionIds() == null ? List.of() : decision.triggeredRuleVersionIds(),
                edgeAssessmentId, ruleBundleVersion, ruleBundleHash == null ? "" : ruleBundleHash);
    }
}
