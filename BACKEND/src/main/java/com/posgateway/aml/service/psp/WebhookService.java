package com.posgateway.aml.service.psp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.psp.WebhookSubscription;
import com.posgateway.aml.repository.WebhookSubscriptionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class WebhookService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebhookService.class);

    private final WebhookSubscriptionRepository subscriptionRepository;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final RestTemplate vgsProxiedRestTemplate;

    @Value("${vgs.proxy.enabled:false}")
    private boolean vgsProxyEnabled;

    public WebhookService(WebhookSubscriptionRepository subscriptionRepository,
            ObjectMapper objectMapper,
            @Qualifier("restTemplate") RestTemplate restTemplate,
            @Qualifier("vgsProxiedRestTemplate") RestTemplate vgsProxiedRestTemplate) {
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.vgsProxiedRestTemplate = vgsProxiedRestTemplate;
    }

    @Async("amlTaskExecutor") // Use our high-throughput pool
    public void sendWebhook(String eventType, Map<String, Object> payload) {
        log.debug("Processing webhooks for event: {}", eventType);

        List<WebhookSubscription> subscriptions = subscriptionRepository.findByEventTypeAndIsActiveTrue(eventType);

        for (WebhookSubscription sub : subscriptions) {
            try {
                // In real app: Add HMAC signature header using sub.getSecretKey()

                RestTemplate client = vgsProxyEnabled ? vgsProxiedRestTemplate : restTemplate;
                String mode = vgsProxyEnabled ? "VGS Proxy" : "Direct";

                log.info("Sending webhook to {} for event {} (Mode: {})", sub.getCallbackUrl(), eventType, mode);
                client.postForObject(sub.getCallbackUrl(), payload, String.class);

                // Reset failure count on success
                if (sub.getFailureCount() > 0) {
                    sub.setFailureCount(0);
                    subscriptionRepository.save(sub);
                }

            } catch (Exception e) {
                log.error("Failed to send webhook to {}: {}", sub.getCallbackUrl(), e.getMessage());
                sub.setFailureCount(sub.getFailureCount() + 1);
                if (sub.getFailureCount() > 5) {
                    sub.setActive(false);
                    log.warn("Disabling webhook subscription {} due to excessive failures", sub.getId());
                }
                subscriptionRepository.save(sub);
            }
        }
    }
}
