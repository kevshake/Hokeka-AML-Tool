package com.posgateway.aml.dto.edge;

public record EdgeAssessmentFindingRequest(
        Long ruleId,
        Long ruleVersionId,
        String action,
        Double score,
        String explanation,
        Boolean triggered
) {}
