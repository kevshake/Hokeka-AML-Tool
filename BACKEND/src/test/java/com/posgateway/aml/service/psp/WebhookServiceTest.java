package com.posgateway.aml.service.psp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.psp.WebhookSubscription;
import com.posgateway.aml.repository.WebhookSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Webhook delivery must be tenant-scoped (an event about one PSP never reaches another PSP's
 * callback) and authenticated (HMAC-SHA256 over timestamp + body).
 */
@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock private WebhookSubscriptionRepository subscriptionRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private RestTemplate vgsProxiedRestTemplate;

    @InjectMocks private WebhookService service;

    // Constructed manually so the real ObjectMapper is used for body serialisation.
    private WebhookService serviceWith(WebhookSubscriptionRepository repo) {
        return new WebhookService(repo, new ObjectMapper(), restTemplate, vgsProxiedRestTemplate);
    }

    @Test
    void tenantScopedSendOnlyQueriesThatTenantsSubscriptions() {
        WebhookService svc = serviceWith(subscriptionRepository);
        when(subscriptionRepository.findByPspIdAndEventTypeAndIsActiveTrue("7", "RISK_ALERT"))
                .thenReturn(List.of());

        svc.sendWebhook(7L, "RISK_ALERT", Map.of("txnId", 1));

        verify(subscriptionRepository).findByPspIdAndEventTypeAndIsActiveTrue("7", "RISK_ALERT");
        // The unscoped query (which would fan out to every tenant) must never be used here.
        verify(subscriptionRepository, never()).findByEventTypeAndIsActiveTrue(any());
    }

    @Test
    void tenantEventWithNoPspIsRefusedRatherThanFannedOut() {
        WebhookService svc = serviceWith(subscriptionRepository);

        svc.sendWebhook(null, "RISK_ALERT", Map.of("txnId", 1));

        verify(subscriptionRepository, never()).findByEventTypeAndIsActiveTrue(any());
        verify(subscriptionRepository, never()).findByPspIdAndEventTypeAndIsActiveTrue(any(), any());
    }

    @Test
    void signatureIsStableKeyedAndBindsTheTimestamp() {
        String sig = WebhookService.sign("secret", 1000L, "{\"a\":1}");
        assertNotNull(sig);
        // Deterministic for the same key/timestamp/body.
        assertEquals(sig, WebhookService.sign("secret", 1000L, "{\"a\":1}"));
        // A different key, timestamp, or body must all change the signature.
        assertNotEquals(sig, WebhookService.sign("other-secret", 1000L, "{\"a\":1}"));
        assertNotEquals(sig, WebhookService.sign("secret", 1001L, "{\"a\":1}"));
        assertNotEquals(sig, WebhookService.sign("secret", 1000L, "{\"a\":2}"));
        assertEquals(64, sig.length(), "hex HMAC-SHA256 is 64 chars");
    }

    @Test
    void noSecretYieldsNoSignature() {
        assertNull(WebhookService.sign(null, 1L, "{}"));
        assertNull(WebhookService.sign("", 1L, "{}"));
    }

    @Test
    void unscopedSendStillUsesThePlatformWideQuery() {
        WebhookService svc = serviceWith(subscriptionRepository);
        when(subscriptionRepository.findByEventTypeAndIsActiveTrue(eq("PLATFORM_NOTICE")))
                .thenReturn(List.<WebhookSubscription>of());

        svc.sendWebhook("PLATFORM_NOTICE", Map.of("msg", "maintenance"));

        verify(subscriptionRepository).findByEventTypeAndIsActiveTrue("PLATFORM_NOTICE");
    }
}
