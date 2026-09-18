package com.posgateway.aml.service.psp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.integration.OutboxEvent;
import com.posgateway.aml.entity.psp.WebhookSubscription;
import com.posgateway.aml.repository.WebhookSubscriptionRepository;
import com.posgateway.aml.repository.integration.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookOutboxServiceTest {

    @Test
    void alertCommitEnqueuesOneOutboxRowPerSubscription() {
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        WebhookSubscriptionRepository subscriptionRepository = mock(WebhookSubscriptionRepository.class);
        when(subscriptionRepository.findByPspIdAndEventTypeAndIsActiveTrue("2", "RISK_ALERT"))
                .thenReturn(List.of(
                        subscription(11L, "https://psp.example/hook-a"),
                        subscription(12L, "https://psp.example/hook-b")));
        when(outboxRepository.existsByEventKey(any())).thenReturn(false);

        WebhookOutboxService service = new WebhookOutboxService(
                outboxRepository, subscriptionRepository, new ObjectMapper());
        service.enqueueForPsp(2L, "RISK_ALERT", Map.of("alertId", 70L),
                "webhook.risk_alert:70");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        OutboxEvent first = captor.getAllValues().get(0);
        assertEquals(OutboxEvent.Channel.WEBHOOK, first.getChannel());
        assertEquals("RISK_ALERT", first.getTopic());
        assertEquals(OutboxEvent.Status.PENDING, first.getStatus());
        assertEquals("https://psp.example/hook-a", first.getDestination());
    }

    @Test
    void duplicateEventKeyIsIdempotent() {
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        WebhookSubscriptionRepository subscriptionRepository = mock(WebhookSubscriptionRepository.class);
        when(subscriptionRepository.findByPspIdAndEventTypeAndIsActiveTrue("2", "RISK_ALERT"))
                .thenReturn(List.of(subscription(11L, "https://psp.example/hook")));
        when(outboxRepository.existsByEventKey("webhook.risk_alert:70:11")).thenReturn(true);

        WebhookOutboxService service = new WebhookOutboxService(
                outboxRepository, subscriptionRepository, new ObjectMapper());
        service.enqueueForPsp(2L, "RISK_ALERT", Map.of("alertId", 70L),
                "webhook.risk_alert:70");

        verify(outboxRepository, never()).save(any());
    }

    private WebhookSubscription subscription(Long id, String url) {
        WebhookSubscription sub = new WebhookSubscription();
        sub.setId(id);
        sub.setCallbackUrl(url);
        sub.setSecretKey("secret");
        sub.setActive(true);
        return sub;
    }
}
