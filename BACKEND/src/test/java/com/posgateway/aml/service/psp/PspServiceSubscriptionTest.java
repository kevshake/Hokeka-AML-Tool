package com.posgateway.aml.service.psp;

import com.posgateway.aml.entity.billing.PricingTier;
import com.posgateway.aml.entity.billing.Subscription;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PricingTierRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.RoleRepository;
import com.posgateway.aml.repository.SubscriptionRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.rules.RuleProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A newly-registered PSP must land on a real subscription (so the billing cycle and entitlements
 * see it), exactly once, resolving the default tier with a sensible fallback.
 */
@ExtendWith(MockitoExtension.class)
class PspServiceSubscriptionTest {

    @Mock private PspRepository pspRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RuleProvisioningService ruleProvisioningService;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PricingTierRepository pricingTierRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private WebhookOutboxService webhookOutboxService;

    @InjectMocks private PspService service;

    private Psp psp;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "defaultTierCode", "STARTER");
        ReflectionTestUtils.setField(service, "trialDays", 14);
        psp = new Psp();
        psp.setPspCode("ACME");
        psp.setCurrency("USD");
        ReflectionTestUtils.setField(psp, "pspId", 1L);
    }

    @Test
    void provisionsTrialSubscriptionOnDefaultTier() {
        when(subscriptionRepository.findActiveByPspId(eq(1L))).thenReturn(Optional.empty());
        PricingTier starter = new PricingTier();
        starter.setTierCode("STARTER");
        when(pricingTierRepository.findByTierCode("STARTER")).thenReturn(Optional.of(starter));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        Optional<Subscription> result = service.provisionDefaultSubscription(psp);

        assertTrue(result.isPresent());
        Subscription sub = result.get();
        assertSame(starter, sub.getPricingTier());
        assertEquals("TRIAL", sub.getStatus());
        assertNotNull(sub.getTrialEndsAt(), "trial window must be set");
        assertEquals("USD", sub.getBillingCurrency());
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void isIdempotentWhenAlreadySubscribed() {
        Subscription existing = new Subscription();
        when(subscriptionRepository.findActiveByPspId(eq(1L))).thenReturn(Optional.of(existing));

        Optional<Subscription> result = service.provisionDefaultSubscription(psp);

        assertSame(existing, result.orElseThrow());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void fallsBackToCheapestActiveTierWhenDefaultMissing() {
        when(subscriptionRepository.findActiveByPspId(eq(1L))).thenReturn(Optional.empty());
        when(pricingTierRepository.findByTierCode("STARTER")).thenReturn(Optional.empty());
        PricingTier cheapest = new PricingTier();
        cheapest.setTierCode("FREE");
        when(pricingTierRepository.findAllActive()).thenReturn(List.of(cheapest));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        Optional<Subscription> result = service.provisionDefaultSubscription(psp);

        assertTrue(result.isPresent());
        assertSame(cheapest, result.get().getPricingTier());
    }

    @Test
    void returnsEmptyWhenNoTierExists() {
        when(subscriptionRepository.findActiveByPspId(eq(1L))).thenReturn(Optional.empty());
        when(pricingTierRepository.findByTierCode("STARTER")).thenReturn(Optional.empty());
        when(pricingTierRepository.findAllActive()).thenReturn(List.of());

        Optional<Subscription> result = service.provisionDefaultSubscription(psp);

        assertTrue(result.isEmpty());
        verify(subscriptionRepository, never()).save(any());
    }

    // --- reactivation-on-payment ---

    @Test
    void reactivatesSuspendedPspWhenDuesCleared() {
        Psp suspended = new Psp();
        suspended.setStatus("SUSPENDED");
        ReflectionTestUtils.setField(suspended, "pspId", 5L);
        when(pspRepository.findById(5L)).thenReturn(Optional.of(suspended));
        when(invoiceRepository.countOutstanding(eq(5L), any())).thenReturn(0L);

        boolean reactivated = service.reactivateIfDuesCleared(5L);

        assertTrue(reactivated);
        assertEquals("ACTIVE", suspended.getStatus());
        verify(pspRepository).save(suspended);
    }

    @Test
    void doesNotReactivateWhenInvoicesStillOutstanding() {
        Psp suspended = new Psp();
        suspended.setStatus("SUSPENDED");
        ReflectionTestUtils.setField(suspended, "pspId", 6L);
        when(pspRepository.findById(6L)).thenReturn(Optional.of(suspended));
        when(invoiceRepository.countOutstanding(eq(6L), any())).thenReturn(2L);

        assertFalse(service.reactivateIfDuesCleared(6L));
        assertEquals("SUSPENDED", suspended.getStatus());
        verify(pspRepository, never()).save(any());
    }

    @Test
    void doesNotReactivateAnAlreadyActivePsp() {
        Psp active = new Psp();
        active.setStatus("ACTIVE");
        ReflectionTestUtils.setField(active, "pspId", 7L);
        when(pspRepository.findById(7L)).thenReturn(Optional.of(active));

        assertFalse(service.reactivateIfDuesCleared(7L));
        verify(pspRepository, never()).save(any());
    }
}
