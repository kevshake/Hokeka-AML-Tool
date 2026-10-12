package com.posgateway.aml.service.decision;

import com.posgateway.aml.entity.assessment.FindingSourceType;
import com.posgateway.aml.service.assessment.FindingDraft;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic resolution of proposed actions from findings: BLOCK &gt; SUSPEND &gt; FLAG &gt; ALLOW.
 */
@Service
public class DecisionResolutionService {

    /**
     * Resolves the winning canonical decision from finding drafts that carry a proposed action.
     */
    public CanonicalDecision resolveFromFindings(List<FindingDraft> findings) {
        if (findings == null || findings.isEmpty()) {
            return CanonicalDecision.ALLOW;
        }
        return findings.stream()
                .filter(f -> f.isTriggered() && f.getProposedAction() != null)
                .max(Comparator
                        .comparing((FindingDraft f) -> rank(CanonicalDecision.fromLegacyAction(f.getProposedAction())))
                        .thenComparing(f -> severityRank(f.getSeverity()))
                        .thenComparing(f -> sourcePriority(f.getSourceType())))
                .map(f -> CanonicalDecision.fromLegacyAction(f.getProposedAction()))
                .orElse(CanonicalDecision.ALLOW);
    }

    /**
     * Returns numeric precedence rank for a canonical decision (higher wins).
     */
    public int rank(CanonicalDecision decision) {
        return switch (Objects.requireNonNullElse(decision, CanonicalDecision.ALLOW)) {
            case BLOCK -> 4;
            case SUSPEND -> 3;
            case FLAG -> 2;
            case ALLOW -> 1;
        };
    }

    private static int severityRank(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "WARN", "MEDIUM" -> 2;
            case "INFO", "LOW" -> 1;
            default -> 0;
        };
    }

    private static int sourcePriority(FindingSourceType sourceType) {
        if (sourceType == null) {
            return 0;
        }
        return switch (sourceType) {
            case LIMIT -> 90;
            case LIST -> 80;
            case SCREENING -> 70;
            case NETWORK -> 60;
            case RULE -> 50;
            case RISK_FACTOR -> 40;
            case ML -> 30;
            default -> 10;
        };
    }
}
