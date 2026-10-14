package com.posgateway.aml.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight guardrails for Management OpenAPI path inclusion without booting the full application.
 */
class ManagementOpenApiConfigTest {

    @Test
    void managementGroupIncludesCoreOperatorSurfaces() {
        String joined = String.join(",", ManagementOpenApiConfig.MANAGEMENT_PATH_PREFIXES);
        assertTrue(joined.contains("/users/**"));
        assertTrue(joined.contains("/admin/**"));
        assertTrue(joined.contains("/edge/nodes/**"));
        assertTrue(joined.contains("/audit/logs/**"));
    }

    @Test
    void managementGroupExcludesSelfServiceProfilePaths() {
        String joined = String.join(",", ManagementOpenApiConfig.MANAGEMENT_PATH_EXCLUSIONS);
        assertTrue(joined.contains("/users/me/**"));
        assertTrue(joined.contains("/psps/auth/**"));
    }
}
