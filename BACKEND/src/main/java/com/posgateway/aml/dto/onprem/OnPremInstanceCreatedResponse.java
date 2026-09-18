package com.posgateway.aml.dto.onprem;

/**
 * One-time create response: includes the plaintext {@code clientSecret} which must be installed
 * on the on-prem instance and is never retrievable again.
 */
public record OnPremInstanceCreatedResponse(
        OnPremInstanceView instance,
        String clientSecret
) {
}
