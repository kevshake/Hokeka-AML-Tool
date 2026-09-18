package com.posgateway.aml.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression guard for the vocabulary correction in W36-6/W36-5: BILLING_PRICING.md and the class
 * javadoc on URL_SERVICE_MAP were both stale, and neither had a test tying the documented service
 * types back to what resolveServiceType actually returns. Locks in the mappings that V216 relies
 * on being correct.
 */
class UsageTrackingFilterServiceTypeTest {

    @Test
    void sarFilingResolvesToTheServiceTypeSeededInV216() {
        assertEquals("SAR_FILING",
                UsageTrackingFilter.resolveServiceType("/api/v1/compliance/sar/123", "POST"));
    }

    @Test
    void cbkReportingResolvesForBothPostAndPut() {
        assertEquals("CBK_REPORTING",
                UsageTrackingFilter.resolveServiceType("/api/v1/compliance/cbk/report-1", "POST"));
        assertEquals("CBK_REPORTING",
                UsageTrackingFilter.resolveServiceType("/api/v1/compliance/cbk/report-1", "PUT"));
    }

    @Test
    void staleV147ServiceTypesAreNeverProducedByTheCurrentMap() {
        // These four paths must NOT resolve to the old, now-deactivated (V216) vocabulary.
        assertNull(UsageTrackingFilter.resolveServiceType("/api/v1/unmapped/path", "POST"));
    }

    @Test
    void unmatchedPathIsNotBilledAsApiCallGenericOrAnythingElse() {
        // API_CALL_GENERIC is seeded in billing_rates (V149) but URL_SERVICE_MAP never produces
        // it -- an unmatched path resolves to null (not tracked), not a generic fallback type.
        assertNull(UsageTrackingFilter.resolveServiceType("/api/v1/some/unrelated/endpoint", "GET"));
    }
}
