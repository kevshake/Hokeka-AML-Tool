package com.posgateway.aml.service.decision;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.service.assessment.AssessmentFindingBuffer;
import com.posgateway.aml.service.assessment.FindingDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes the final decision from in-flight findings (compute-all) and compares shadow vs legacy paths.
 */
@Service
public class DecisionOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(DecisionOrchestrator.class);

    private final DecisionResolutionService decisionResolutionService;

    public DecisionOrchestrator(DecisionResolutionService decisionResolutionService) {
        this.decisionResolutionService = decisionResolutionService;
    }

    /**
     * Resolves canonical and legacy actions from the thread-local finding buffer for the active assessment.
     */
    public ResolvedDecision resolveBufferedFindings(TransactionEntity transaction, Double score,
                                                    List<String> supplementalReasons) {
        List<FindingDraft> buffered = new ArrayList<>(AssessmentFindingBuffer.snapshot());
        CanonicalDecision canonical = decisionResolutionService.resolveFromFindings(buffered);
        String legacyAction = CanonicalDecision.toLegacyAction(canonical);
        List<String> reasons = supplementalReasons != null ? new ArrayList<>(supplementalReasons) : new ArrayList<>();
        if (reasons.isEmpty()) {
            buffered.stream()
                    .filter(FindingDraft::isTriggered)
                    .map(FindingDraft::getExplanation)
                    .filter(ex -> ex != null && !ex.isBlank())
                    .forEach(reasons::add);
        }
        double resolvedScore = score != null ? score : 0.0;
        for (FindingDraft draft : buffered) {
            if (draft.getScore() != null) {
                resolvedScore = Math.max(resolvedScore, draft.getScore());
            }
        }
        return new ResolvedDecision(canonical, legacyAction, resolvedScore, reasons);
    }

    /**
     * Logs when shadow orchestrator resolution disagrees with the legacy short-circuit decision.
     */
    public void logShadowDisagreement(TransactionEntity transaction, String legacyAction,
                                      ResolvedDecision orchestrated) {
        if (transaction == null || orchestrated == null || legacyAction == null) {
            return;
        }
        CanonicalDecision legacyCanonical = CanonicalDecision.fromLegacyAction(legacyAction);
        if (legacyCanonical != orchestrated.canonical()) {
            logger.warn("Decision orchestrator shadow disagreement txnId={} legacy={} orchestrated={}",
                    transaction.getTxnId(), legacyCanonical, orchestrated.canonical());
        }
    }

    /**
     * Pair of canonical (v2) and legacy (v1) decisions plus supporting reasons.
     */
    public record ResolvedDecision(CanonicalDecision canonical, String legacyAction,
                                   double score, List<String> reasons) {
    }
}
