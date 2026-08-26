package com.posgateway.aml.controller.psp;

import com.posgateway.aml.entity.psp.WebhookSubscription;
import com.posgateway.aml.repository.WebhookSubscriptionRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies W36-2: POST /webhooks/subscribe (documented in PSP_API_GUIDE.md, previously 404 --
 * no controller existed at all) is now real, scoped strictly to the caller's own PSP.
 */
@ExtendWith(MockitoExtension.class)
class WebhookSubscriptionControllerTest {

    @Mock
    private WebhookSubscriptionRepository subscriptionRepository;
    @Mock
    private PspIsolationService pspIsolationService;

    private WebhookSubscriptionController controller;

    @BeforeEach
    void setUp() {
        controller = new WebhookSubscriptionController(subscriptionRepository, pspIsolationService);
    }

    @Test
    void subscribeRejectsNonHttpsCallbackUrl() {
        var request = new WebhookSubscriptionController.SubscribeRequest("http://insecure.example.com", "RISK_ALERT");

        assertThrows(IllegalArgumentException.class, () -> controller.subscribe(request));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void subscribeRejectsUnknownEventType() {
        var request = new WebhookSubscriptionController.SubscribeRequest("https://example.com/hook", "NOT_A_REAL_TYPE");

        assertThrows(IllegalArgumentException.class, () -> controller.subscribe(request));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void subscribeScopesTheNewSubscriptionToTheCallersOwnPsp() {
        when(pspIsolationService.getCurrentUserPspId()).thenReturn(7L);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new WebhookSubscriptionController.SubscribeRequest("https://example.com/hook", "RISK_ALERT");
        var response = controller.subscribe(request);

        WebhookSubscription saved = response.getBody();
        assertEquals("7", saved.getPspId());
    }

    @Test
    void unsubscribeAcrossTenantsThrowsSecurityException() {
        WebhookSubscription other = WebhookSubscription.builder().pspId("99").build();
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(other));
        when(pspIsolationService.getCurrentUserPspId()).thenReturn(7L); // caller belongs to PSP 7

        assertThrows(SecurityException.class, () -> controller.unsubscribe(1L));
        verify(subscriptionRepository, never()).deleteById(anyLong());
    }

    @Test
    void unsubscribeSameTenantSucceeds() {
        WebhookSubscription mine = WebhookSubscription.builder().pspId("7").build();
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(mine));
        when(pspIsolationService.getCurrentUserPspId()).thenReturn(7L);

        controller.unsubscribe(1L);

        verify(subscriptionRepository).deleteById(1L);
    }

    @Test
    void listSubscriptionsForPspScopedCallerUsesTheScopedQuery() {
        when(pspIsolationService.getCurrentUserPspId()).thenReturn(7L);
        when(subscriptionRepository.findByPspId("7")).thenReturn(List.of());

        controller.listSubscriptions();

        verify(subscriptionRepository).findByPspId("7");
        verify(subscriptionRepository, never()).findAll();
    }

    @Test
    void listSubscriptionsForPlatformAdminUsesTheUnscopedQuery() {
        when(pspIsolationService.getCurrentUserPspId()).thenReturn(0L); // platform admin
        when(subscriptionRepository.findAll()).thenReturn(List.of());

        controller.listSubscriptions();

        verify(subscriptionRepository).findAll();
        verify(subscriptionRepository, never()).findByPspId(any());
    }
}
