package com.posgateway.aml.dto.onprem;

import java.time.Instant;

/**
 * Lease grant returned by central Hokeka auth after successful client-credentials authentication.
 */
public record OnPremLeaseResponse(
        String instanceId,
        Long pspId,
        Instant validUntil,
        Instant nextCheckAt,
        int approvedDays,
        int checkIntervalDays,
        String leaseToken,
        String tokenType,
        long issuedAtEpochSeconds
) {
}
