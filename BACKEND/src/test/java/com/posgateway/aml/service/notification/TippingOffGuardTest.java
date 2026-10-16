package com.posgateway.aml.service.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TippingOffGuardTest {

    /** Verifies customer copy drops investigation and sar language. */
    @Test
    void customerCopyDropsInvestigationAndSarLanguage() {
        String sanitized = TippingOffGuard.sanitizeCustomerText(
                "Your account is under investigation for a SAR / suspicious activity case.");
        assertFalse(TippingOffGuard.containsTippingOffLanguage(sanitized));
        assertTrue(sanitized.toLowerCase().contains("account"));
    }

    /** Verifies internal analyst copy may stay precise. */
    @Test
    void internalAnalystCopyMayStayPrecise() {
        String internal = "Case CASE-1 escalated — file SAR, suspicion formed.";
        assertTrue(TippingOffGuard.containsTippingOffLanguage(internal));
        assertEquals(internal, TippingOffGuard.allowInternal(internal));
    }
}
