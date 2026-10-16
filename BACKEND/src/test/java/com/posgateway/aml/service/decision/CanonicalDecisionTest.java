package com.posgateway.aml.service.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalDecisionTest {

    @Test
    void mapsHoldToSuspendAndAlertToFlag() {
        assertEquals(CanonicalDecision.SUSPEND, CanonicalDecision.fromLegacyAction("HOLD"));
        assertEquals(CanonicalDecision.FLAG, CanonicalDecision.fromLegacyAction("ALERT"));
    }

    @Test
    void mergeLegacyUsesPrecedence() {
        assertEquals("HOLD", CanonicalDecision.mergeLegacyActions("ALLOW", "HOLD"));
        assertEquals("ALERT", CanonicalDecision.mergeLegacyActions("ALLOW", "ALERT"));
        assertEquals("BLOCK", CanonicalDecision.mergeLegacyActions("ALLOW", "BLOCK"));
    }
}
