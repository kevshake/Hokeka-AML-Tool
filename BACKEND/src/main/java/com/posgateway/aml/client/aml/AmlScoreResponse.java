package com.posgateway.aml.client.aml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response payload from {@code POST /internal/v1/aml/score}.
 *
 * <p>{@code cacheLayer} is one of {@code L1_AEROSPIKE} or {@code COMPUTED}.
 * {@code indicators} carries free-form risk signals from the microservice
 * (e.g. {@code SANCTIONS_FLAGGED}, {@code SANCTIONS_REVIEW}). Empty when none.
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
        String cacheLayer,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> indicators
) {}
