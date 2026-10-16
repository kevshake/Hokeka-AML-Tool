package com.posgateway.aml.dto.edge;

public record EdgeAssessmentIngestResponse(
        String edgeAssessmentId,
        String assessmentId,
        boolean duplicate
) {}
