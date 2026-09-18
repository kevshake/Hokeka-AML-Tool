package com.posgateway.aml.service.psp;

import com.posgateway.aml.entity.integration.OutboxEvent;
import com.posgateway.aml.entity.psp.WebhookSubscription;
import com.posgateway.aml.repository.WebhookSubscriptionRepository;
import com.posgateway.aml.repository.integration.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookOutboxDispatcherTest {

    @Test
    void successfulDeliveryMarksOutboxPublishedAndResetsSubscriptionFailures() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        WebhookSubscriptionRepository subscriptionRepository = mock(WebhookSubscriptionRepository.class);
        WebhookDeliveryClient deliveryClient = mock(WebhookDeliveryClient.class);

        OutboxEvent event = webhookEvent();
        WebhookSubscription subscription = activeSubscription();
        subscription.setFailureCount(2);

        when(repository.lockReadyWebhookBatch(10)).thenReturn(List.of(event));
        when(subscriptionRepository.findById(5L)).thenReturn(Optional.of(subscription));

        new WebhookOutboxDispatcher(repository, subscriptionRepository, deliveryClient, 10, 3)
                .dispatchReadyDeliveries();

        assertEquals(OutboxEvent.Status.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertEquals(0, subscription.getFailureCount());
        verify(deliveryClient).deliver(subscription, "RISK_ALERT", event.getPayload());
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void failedDeliverySchedulesRetryWithRecordedError() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        WebhookSubscriptionRepository subscriptionRepository = mock(WebhookSubscriptionRepository.class);
        WebhookDeliveryClient deliveryClient = mock(WebhookDeliveryClient.class);

        OutboxEvent event = webhookEvent();
        WebhookSubscription subscription = activeSubscription();
        LocalDateTime before = LocalDateTime.now();

        when(repository.lockReadyWebhookBatch(10)).thenReturn(List.of(event));
        when(subscriptionRepository.findById(5L)).thenReturn(Optional.of(subscription));
        doThrow(new IllegalStateException("connection refused"))
                .when(deliveryClient).deliver(any(), any(), any());

        new WebhookOutboxDispatcher(repository, subscriptionRepository, deliveryClient, 10, 3)
                .dispatchReadyDeliveries();

        assertEquals(OutboxEvent.Status.PENDING, event.getStatus());
        assertEquals(1, event.getAttempts());
        assertTrue(event.getNextAttemptAt().isAfter(before));
        assertTrue(event.getLastError().contains("connection refused"));
        assertEquals(1, subscription.getFailureCount());
    }

    @Test
    void maxAttemptsMarksOutboxFailed() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        WebhookSubscriptionRepository subscriptionRepository = mock(WebhookSubscriptionRepository.class);
        WebhookDeliveryClient deliveryClient = mock(WebhookDeliveryClient.class);

        OutboxEvent event = webhookEvent();
        event.setAttempts(2);
        WebhookSubscription subscription = activeSubscription();

        when(repository.lockReadyWebhookBatch(10)).thenReturn(List.of(event));
        when(subscriptionRepository.findById(5L)).thenReturn(Optional.of(subscription));
        doThrow(new IllegalStateException("timeout"))
                .when(deliveryClient).deliver(any(), any(), any());

        new WebhookOutboxDispatcher(repository, subscriptionRepository, deliveryClient, 10, 3)
                .dispatchReadyDeliveries();

        assertEquals(OutboxEvent.Status.FAILED, event.getStatus());
        assertEquals(3, event.getAttempts());
        assertNotNull(event.getPublishedAt());
    }

    private OutboxEvent webhookEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setEventKey("webhook.risk_alert:70:5");
        event.setChannel(OutboxEvent.Channel.WEBHOOK);
        event.setTopic("RISK_ALERT");
        event.setSubscriptionId(5L);
        event.setDestination("https://psp.example/hook");
        event.setPayload("{\"alertId\":70}");
        return event;
    }

    private WebhookSubscription activeSubscription() {
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setId(5L);
        subscription.setCallbackUrl("https://psp.example/hook");
        subscription.setSecretKey("secret");
        subscription.setActive(true);
        return subscription;
    }
}
