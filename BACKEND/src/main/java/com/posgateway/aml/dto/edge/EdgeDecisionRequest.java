package com.posgateway.aml.dto.edge;

import java.util.Map;

/**
 * Edge → Control Plane AI decision request (mTLS authenticated).
 */
public record EdgeDecisionRequest(
        String engine,
        String baselineDecision,
        Map<String, Object> features,
        boolean async
) {
}
