package com.posgateway.aml.service.psp;

import com.posgateway.aml.entity.billing.PricingTier;
import com.posgateway.aml.entity.billing.Subscription;
import com.posgateway.aml.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Entitlements must come from the PSP's real active subscription tier — the feature list and the
 * monthly check quota — and a PSP without a subscription must be grandfathered (not locked out)
 * while still receiving no premium features and no quota cap.
 */
@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @InjectMocks private EntitlementService service;

    @Test
    void resolvesFeaturesAndQuotaFromActiveTier() {
        PricingTier tier = new PricingTier();
        tier.setTierCode("GROWTH");
        tier.setMaxChecksPerMonth(10_000);
        tier.setFeatures(List.of("case_management", "sar_generation", "custom_rules"));
        Subscription sub = new Subscription();
        sub.setPricingTier(tier);
        when(subscriptionRepository.findActiveByPspId(eq(7L))).thenReturn(Optional.of(sub));

        assertEquals("GROWTH", service.planCode(7L));
        assertEquals(10_000, service.monthlyCheckLimit(7L));
        assertTrue(service.hasFeature(7L, "custom_rules"));
        assertFalse(service.hasFeature(7L, "white_label"), "tier does not grant white_label");
    }

    @Test
    void noSubscriptionIsGrandfatheredAndUncapped() {
        when(subscriptionRepository.findActiveByPspId(eq(9L))).thenReturn(Optional.empty());

        assertEquals("NONE", service.planCode(9L));
        assertNull(service.monthlyCheckLimit(9L), "no plan ⇒ no monthly cap");
        assertTrue(service.hasFeature(9L, "anything"), "grandfathered until a plan is assigned");
    }

    @Test
    void nullPspHasNoEntitlements() {
        assertEquals("NONE", service.planCode(null));
        assertNull(service.monthlyCheckLimit(null));
    }
}
