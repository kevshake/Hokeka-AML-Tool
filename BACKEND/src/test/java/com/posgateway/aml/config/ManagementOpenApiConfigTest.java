package com.posgateway.aml.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void managementGroupExcludesOperatorOnlyAiAdminPaths() {
        String prefixes = String.join(",", ManagementOpenApiConfig.MANAGEMENT_PATH_PREFIXES);
        assertFalse(prefixes.contains("/ai/"));
        String exclusions = String.join(",", ManagementOpenApiConfig.MANAGEMENT_PATH_EXCLUSIONS);
        assertTrue(exclusions.contains("/ai/**"));
        assertFalse(ManagementOpenApiConfig.ManagementPathRules.isDocumentedManagementPath("/ai/status"));
        assertFalse(ManagementOpenApiConfig.ManagementPathRules.isDocumentedManagementPath("/ai/engines"));
        assertFalse(ManagementOpenApiConfig.ManagementPathRules.isDocumentedManagementPath("/ai/audit/case/1"));
    }

    @Test
    void publicSanitizerFlagsForbiddenAiAdminPaths() {
        assertTrue(ManagementOpenApiConfig.ManagementPublicSpecSanitizer
                .isForbiddenPublicManagementPath("/ai/engines"));
        assertTrue(ManagementOpenApiConfig.ManagementPublicSpecSanitizer
                .isForbiddenPublicManagementPath("/ai/status"));
        assertFalse(ManagementOpenApiConfig.ManagementPublicSpecSanitizer
                .isForbiddenPublicManagementPath("/admin/psp-billing"));
    }
}
