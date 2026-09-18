package com.posgateway.aml.entity.onprem;

/**
 * Lifecycle of an on-prem PSP AML instance registered with Hokeka central auth.
 * Only {@link #ACTIVE} instances may receive or renew a service lease.
 */
public enum OnPremInstanceStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED
}
