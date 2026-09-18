package com.posgateway.aml.service.psp;

import com.posgateway.aml.entity.integration.OutboxEvent;
import com.posgateway.aml.entity.psp.WebhookSubscription;
import com.posgateway.aml.repository.WebhookSubscriptionRepository;
import com.posgateway.aml.repository.integration.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Delivers {@link OutboxEvent.Channel#WEBHOOK} rows with retries, durable failure state, and
 * subscription auto-disable after consecutive delivery failures.
 */
@Service
public class WebhookOutboxDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(WebhookOutboxDispatcher.class);

    static final int MAX_FAILURES_BEFORE_DISABLE = 5;

    private final OutboxEventRepository repository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryClient deliveryClient;
    private final int batchSize;
    private final int maxAttempts;

    public WebhookOutboxDispatcher(OutboxEventRepository repository,
                                   WebhookSubscriptionRepository subscriptionRepository,
                                   WebhookDeliveryClient deliveryClient,
                                   @Value("${webhook.outbox.batch-size:50}") int batchSize,
                                   @Value("${webhook.outbox.max-attempts:15}") int maxAttempts) {
        this.repository = repository;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryClient = deliveryClient;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${webhook.outbox.dispatch-delay-ms:1000}")
    @Transactional
    public void dispatchReadyDeliveries() {
        List<OutboxEvent> events = repository.lockReadyWebhookBatch(batchSize);
        for (OutboxEvent event : events) {
            dispatchOne(event);
        }
    }

    private void dispatchOne(OutboxEvent event) {
        Optional<WebhookSubscription> subscriptionOpt =
                subscriptionRepository.findById(event.getSubscriptionId());
        if (subscriptionOpt.isEmpty() || !subscriptionOpt.get().isActive()) {
            event.setStatus(OutboxEvent.Status.FAILED);
            event.setLastError("Subscription inactive or missing: id=" + event.getSubscriptionId());
            event.setPublishedAt(LocalDateTime.now());
            logger.warn("Webhook outbox delivery abandoned: eventKey={} reason={}",
                    event.getEventKey(), event.getLastError());
            return;
        }

        WebhookSubscription subscription = subscriptionOpt.get();
        try {
            deliveryClient.deliver(subscription, event.getTopic(), event.getPayload());
            event.setStatus(OutboxEvent.Status.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);
            if (subscription.getFailureCount() > 0) {
                subscription.setFailureCount(0);
                subscriptionRepository.save(subscription);
            }
        } catch (Exception failure) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setLastError(limit(failure.getMessage(), 4000));

            subscription.setFailureCount(subscription.getFailureCount() + 1);
            if (subscription.getFailureCount() >= MAX_FAILURES_BEFORE_DISABLE) {
                subscription.setActive(false);
                logger.warn("Disabling webhook subscription {} after {} consecutive failures",
                        subscription.getId(), subscription.getFailureCount());
            }
            subscriptionRepository.save(subscription);

            if (attempts >= maxAttempts) {
                event.setStatus(OutboxEvent.Status.FAILED);
                event.setPublishedAt(LocalDateTime.now());
                logger.error("Webhook outbox delivery permanently failed: eventKey={} attempts={} error={}",
                        event.getEventKey(), attempts, failure.getMessage());
            } else {
                long retrySeconds = Math.min(300L, 1L << Math.min(attempts, 8));
                event.setNextAttemptAt(LocalDateTime.now().plusSeconds(retrySeconds));
                logger.warn("Webhook outbox delivery failed: eventKey={} attempt={} retrySeconds={} error={}",
                        event.getEventKey(), attempts, retrySeconds, failure.getMessage());
            }
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
