package com.posgateway.aml.client.aml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response payload from {@code POST /internal/v1/aml/score}.
 *
 * <p>{@code cacheLayer} is one of {@code L1_AEROSPIKE} or {@code COMPUTED}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AmlScoreResponse(
        String transactionId,
        Long pspId,
        double riskScore,
        String decision,
        String riskLevel,
        String source,
        long processingTimeMs,
        String cacheLayer
) {}
