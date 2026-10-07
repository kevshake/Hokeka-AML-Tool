package com.posgateway.aml.service.ai.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiTightenOnlyAuthorityTest {

    private final AiTightenOnlyAuthority authority = new AiTightenOnlyAuthority();

    @Test
    void shadowModeNeverAppliesMutation() {
        var result = authority.apply("ALLOW", AiBranch.ESCALATE_UP, sampleContext(), true, true);
        assertEquals("ALLOW", result.finalDecision());
        assertTrue(result.wouldApply());
    }

    @Test
    void escalateUpNeverBelowBaseline() {
        assertTrue(AiTightenOnlyAuthority.wouldDowngrade("HOLD", "ALERT"));
        assertFalse(AiTightenOnlyAuthority.wouldDowngrade("ALERT", "HOLD"));
    }

    @Test
    void cannotDowngradeBlockBaseline() {
        var result = authority.apply("BLOCK", AiBranch.ESCALATE_UP, sampleContext(), false, true);
        assertEquals("BLOCK", result.finalDecision());
    }

    @Test
    void likelyFalsePositiveNeverAutoDismisses() {
        var mutation = AiTightenOnlyAuthority.mutationFor(AiBranch.LIKELY_FALSE_POSITIVE);
        assertFalse(AiTightenOnlyAuthority.isAllowedMutation(mutation, "REVIEW", sampleContext()));
    }

    private static AiDecisionContext sampleContext() {
        return AiDecisionContext.builder(AiEngineType.ALERT_TRIAGE)
                .baselineDecision("ALLOW")
                .build();
    }
}
