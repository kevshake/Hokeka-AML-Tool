package com.posgateway.aml.service.jev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JevTightenOnlyAuthorityTest {

    private final JevTightenOnlyAuthority authority = new JevTightenOnlyAuthority();

    @Test
    void shadowModeNeverAppliesMutation() {
        var result = authority.apply("ALLOW", JevBranch.ESCALATE_UP, sampleContext(), true, true);
        assertEquals("ALLOW", result.finalDecision());
        assertTrue(result.wouldApply());
    }

    @Test
    void escalateUpNeverBelowBaseline() {
        assertTrue(JevTightenOnlyAuthority.wouldDowngrade("HOLD", "ALERT"));
        assertFalse(JevTightenOnlyAuthority.wouldDowngrade("ALERT", "HOLD"));
    }

    @Test
    void cannotDowngradeBlockBaseline() {
        var result = authority.apply("BLOCK", JevBranch.ESCALATE_UP, sampleContext(), false, true);
        assertEquals("BLOCK", result.finalDecision());
    }

    @Test
    void likelyFalsePositiveNeverAutoDismisses() {
        var mutation = JevTightenOnlyAuthority.mutationFor(JevBranch.LIKELY_FALSE_POSITIVE);
        assertFalse(JevTightenOnlyAuthority.isAllowedMutation(mutation, "REVIEW", sampleContext()));
    }

    private static JevDecisionContext sampleContext() {
        return JevDecisionContext.builder(JevEngineType.ALERT_TRIAGE)
                .baselineDecision("ALLOW")
                .build();
    }
}
