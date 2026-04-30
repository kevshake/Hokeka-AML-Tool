package com.posgateway.aml.client.aml;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Request payload for {@code POST /internal/v1/aml/score}.
 *
 * <p>{@code pspId} is mandatory upstream.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AmlScoreRequest(
        String transactionId,
        Long pspId,
        String merchantId,
        BigDecimal amount,
        Long amountCents,
        String currency,
        String transactionType,
        String country,
        String customerId,
        String panHash
) {}
