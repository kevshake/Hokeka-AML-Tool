package com.posgateway.aml.edge.tunnel;

/**
 * Cloud half of the §9 double gate: whether a PSP has enabled "allow Hokeka support access".
 * A {@code PLATFORM_USER} pull is refused with {@code 403 SUPPORT_ACCESS_DISABLED} without
 * contacting the Edge unless this answers {@code true}.
 */
public interface SupportAccessPolicy {

    /** True when {@code psps.support_access_enabled} is set for {@code pspId}. */
    boolean isSupportAccessEnabled(long pspId);
}
