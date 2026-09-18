package com.posgateway.aml.service.psp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.integration.OutboxEvent;
import com.posgateway.aml.entity.psp.WebhookSubscription;
import com.posgateway.aml.repository.WebhookSubscriptionRepository;
import com.posgateway.aml.repository.integration.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Transactionally enqueues outbound webhook deliveries alongside the business write that triggered
 * them. Actual HTTP delivery is performed by {@link WebhookOutboxDispatcher}.
 */
@Service
public class WebhookOutboxService {

    private static final Logger log = LoggerFactory.getLogger(WebhookOutboxService.class);

    private final OutboxEventRepository outboxRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    public WebhookOutboxService(OutboxEventRepository outboxRepository,
                                WebhookSubscriptionRepository subscriptionRepository,
                                ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Enqueue one durable delivery row per active subscription for the tenant and event type.
     *
     * @param eventKeyPrefix stable prefix; subscription id is appended for per-target deduplication
     */
    @Transactional
    public void enqueueForPsp(Long pspId, String eventType, Map<String, Object> payload,
                              String eventKeyPrefix) {
        if (pspId == null) {
            log.warn("Refusing to enqueue '{}' webhook with no PSP scope", eventType);
            return;
        }
        List<WebhookSubscription> subscriptions =
                subscriptionRepository.findByPspIdAndEventTypeAndIsActiveTrue(
                        String.valueOf(pspId), eventType);
        if (subscriptions.isEmpty()) {
            log.debug("No active webhook subscriptions for PSP {} event {}", pspId, eventType);
            return;
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot serialise webhook payload for event " + eventType, e);
        }

        for (WebhookSubscription subscription : subscriptions) {
            String eventKey = eventKeyPrefix + ":" + subscription.getId();
            if (outboxRepository.existsByEventKey(eventKey)) {
                continue;
            }
            OutboxEvent event = new OutboxEvent();
            event.setEventKey(eventKey);
            event.setChannel(OutboxEvent.Channel.WEBHOOK);
            event.setTopic(eventType);
            event.setDestination(subscription.getCallbackUrl());
            event.setSubscriptionId(subscription.getId());
            event.setPartitionKey(String.valueOf(pspId));
            event.setPayload(body);
            outboxRepository.save(event);
            log.debug("Queued webhook delivery: eventKey={} url={}", eventKey,
                    subscription.getCallbackUrl());
        }
    }
}
