package com.posgateway.aml.service.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecisionOrchestratorModeServiceTest {

    @Test
    void defaultsToShadowWhenPropertyMatchesSpringDefault() {
        DecisionOrchestratorModeService service = new DecisionOrchestratorModeService("shadow");
        assertEquals("shadow", service.getMode());
    }

    @Test
    void runtimeToggleAcceptsLegacyShadowLive() {
        DecisionOrchestratorModeService service = new DecisionOrchestratorModeService("legacy");
        assertEquals("live", service.setMode("LIVE"));
        assertEquals("shadow", service.setMode("shadow"));
    }

    @Test
    void rejectsUnknownModeTokens() {
        DecisionOrchestratorModeService service = new DecisionOrchestratorModeService("shadow");
        assertThrows(IllegalArgumentException.class, () -> service.setMode("experimental"));
    }
}
