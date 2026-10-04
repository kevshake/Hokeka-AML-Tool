package com.posgateway.aml.dto.edge;

import java.util.List;
import java.util.Map;

/**
 * Body of {@code POST /api/v1/edge/assessments}. Field names match the edge host
 * {@code EdgeAssessmentEnvelope}.
 */
public record EdgeAssessmentIngestRequest(
        String edgeAssessmentId,
        String bundleManifestHash,
        List<Long> ruleVersionIds,
        List<EdgeAssessmentFindingRequest> findings,
        String decision,
        Integer score,
        Map<String, Object> featureSnapshot,
        Long latencyMs
) {}
