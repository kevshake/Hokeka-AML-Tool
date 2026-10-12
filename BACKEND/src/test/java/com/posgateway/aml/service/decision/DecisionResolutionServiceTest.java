package com.posgateway.aml.service.decision;

import com.posgateway.aml.entity.assessment.FindingSourceType;
import com.posgateway.aml.service.assessment.FindingDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Precedence table tests for WP-02 orchestrator resolution.
 */
class DecisionResolutionServiceTest {

    private final DecisionResolutionService service = new DecisionResolutionService();

    @Test
    void blockWinsOverSuspendFlagAndAllow() {
        List<FindingDraft> findings = List.of(
                draft("ALLOW", FindingSourceType.ML, "INFO"),
                draft("FLAG", FindingSourceType.RULE, "WARN"),
                draft("HOLD", FindingSourceType.SCREENING, "HIGH"),
                draft("BLOCK", FindingSourceType.LIMIT, "CRITICAL"));
        assertEquals(CanonicalDecision.BLOCK, service.resolveFromFindings(findings));
    }

    @Test
    void suspendWinsOverFlagAndAllow() {
        List<FindingDraft> findings = List.of(
                draft("ALLOW", FindingSourceType.ML, "INFO"),
                draft("ALERT", FindingSourceType.RULE, "WARN"),
                draft("SUSPEND", FindingSourceType.NETWORK, "HIGH"));
        assertEquals(CanonicalDecision.SUSPEND, service.resolveFromFindings(findings));
    }

    @Test
    void flagWinsOverAllow() {
        List<FindingDraft> findings = List.of(
                draft("ALLOW", FindingSourceType.ML, "INFO"),
                draft("FLAG", FindingSourceType.RULE, "WARN"));
        assertEquals(CanonicalDecision.FLAG, service.resolveFromFindings(findings));
    }

    private static FindingDraft draft(String action, FindingSourceType sourceType, String severity) {
        return FindingDraft.create()
                .sourceType(sourceType)
                .triggered(true)
                .proposedAction(action)
                .severity(severity);
    }
}
