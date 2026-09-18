package com.posgateway.aml.dto.onprem;

import com.posgateway.aml.entity.onprem.OnPremInstanceStatus;

import java.time.Instant;

/** Admin / operator view of an on-prem instance (never includes the secret). */
public record OnPremInstanceView(
        Long id,
        Long pspId,
        String instanceId,
        String clientId,
        String displayName,
        OnPremInstanceStatus status,
        Integer approvedDays,
        Instant leaseUntil,
        Instant nextCheckAt,
        Instant lastSeenAt,
        String hostname,
        String agentVersion,
        Instant revokedAt,
        String revokedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
