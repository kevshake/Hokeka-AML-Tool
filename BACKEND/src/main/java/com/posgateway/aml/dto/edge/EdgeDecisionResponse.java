package com.posgateway.aml.dto.edge;

import java.util.List;

/**
 * Control Plane → Edge AI decision response. {@code finalDecision} always reflects the
 * deterministic baseline when AI is advisory or fallback occurred.
 */
public record EdgeDecisionResponse(
        String baselineDecision,
        String finalDecision,
        String aiRecommendation,
        Double riskScore,
        Double confidence,
        List<String> reasons,
        List<String> citedSignals,
        Long auditId,
        boolean aiApplied,
        boolean fallback,
        String fallbackReason,
        boolean advisory
) {
}
