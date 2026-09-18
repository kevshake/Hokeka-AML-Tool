package com.posgateway.aml.config.tenant;

/**
 * Constants for the Hibernate {@code @Filter} applied to PSP-scoped entities.
 */
public final class PspTenantFilter {

    public static final String NAME = "pspTenantFilter";
    public static final String PARAM = "pspId";

    /** Sentinel bound for platform administrators — matches {@code PspIsolationService} PSP id 0. */
    public static final long PLATFORM_ADMIN_PSP_ID = 0L;

    private PspTenantFilter() {}
}
