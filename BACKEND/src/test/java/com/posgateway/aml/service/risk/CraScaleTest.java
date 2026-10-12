package com.posgateway.aml.service.risk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraScaleTest {

    @Test
    void normalizesLegacyFractionToHundred() {
        assertEquals(42.0, CraScale.normalizeToHundred(0.42), 0.001);
    }

    @Test
    void leavesHundredScaleUnchanged() {
        assertEquals(72.5, CraScale.normalizeToHundred(72.5), 0.001);
    }
}
