package com.posgateway.aml.service.psp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.psp.WebhookSubscription;
import com.posgateway.aml.repository.WebhookSubscriptionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Outbound webhook delivery to a tenant's registered callback URLs.
 *
 * <p>Two properties this service must never violate:
 * <ul>
 *   <li><b>Tenant scoping</b> — an event about one PSP is delivered ONLY to that PSP's own
 *       subscriptions. {@link #sendWebhook(Long, String, Map)} is the correct entry point; the
 *       unscoped {@link #sendWebhook(String, Map)} is reserved for genuinely platform-wide events.</li>
 *   <li><b>Authenticity</b> — every request carries an {@code X-Hokeka-Signature} HMAC-SHA256 of the
 *       exact JSON body, keyed with the subscription's secret, plus a timestamp the receiver can use
 *       to reject replays. Without this a receiver cannot distinguish us from any other caller.</li>
 * </ul>
 */
@Service
public class WebhookService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebhookService.class);

    /** Header carrying the hex HMAC-SHA256 of the request body. */
    static final String SIGNATURE_HEADER = "X-Hokeka-Signature";
    /** Header carrying the signing timestamp (epoch millis) so receivers can bound replay windows. */
    static final String TIMESTAMP_HEADER = "X-Hokeka-Timestamp";
    static final String EVENT_HEADER = "X-Hokeka-Event";

    private static final int MAX_FAILURES_BEFORE_DISABLE = 5;

    private final WebhookSubscriptionRepository subscriptionRepository;
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

    /**
     * Deliver a tenant-scoped event to that PSP's subscriptions only. Use this for anything derived
     * from a specific PSP's data (alerts, cases, merchant status, billing).
     */
    @Async("amlTaskExecutor")
    public void sendWebhook(Long pspId, String eventType, Map<String, Object> payload) {
        if (pspId == null) {
            log.warn("Refusing to dispatch '{}' webhook with no PSP scope — a tenant event must name "
                    + "its tenant, otherwise it would fan out cross-tenant", eventType);
            return;
        }
        deliver(subscriptionRepository.findByPspIdAndEventTypeAndIsActiveTrue(String.valueOf(pspId), eventType),
                eventType, payload);
    }

    /**
     * Deliver a genuinely platform-wide event to every active subscription for the type.
     * <b>Do not use for tenant data</b> — see {@link #sendWebhook(Long, String, Map)}.
     */
    @Async("amlTaskExecutor")
    public void sendWebhook(String eventType, Map<String, Object> payload) {
        deliver(subscriptionRepository.findByEventTypeAndIsActiveTrue(eventType), eventType, payload);
    }

    private void deliver(List<WebhookSubscription> subscriptions, String eventType,
                         Map<String, Object> payload) {
        if (subscriptions.isEmpty()) {
            log.debug("No active webhook subscriptions for event {}", eventType);
            return;
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Cannot serialise webhook payload for event {}: {}", eventType, e.getMessage());
            return;
        }

        RestTemplate client = vgsProxyEnabled ? vgsProxiedRestTemplate : restTemplate;

        for (WebhookSubscription sub : subscriptions) {
            try {
                deliverSynchronously(client, sub, eventType, body);
                if (sub.getFailureCount() > 0) {
                    sub.setFailureCount(0);
                    subscriptionRepository.save(sub);
                }
            } catch (Exception e) {
                log.error("Failed to send webhook to {}: {}", sub.getCallbackUrl(), e.getMessage());
                sub.setFailureCount(sub.getFailureCount() + 1);
                if (sub.getFailureCount() > MAX_FAILURES_BEFORE_DISABLE) {
                    sub.setActive(false);
                    log.warn("Disabling webhook subscription {} after {} consecutive failures",
                            sub.getId(), sub.getFailureCount());
                }
                subscriptionRepository.save(sub);
            }
        }
    }

    /** Best-effort synchronous delivery for legacy {@code @Async} callers. */
    static void deliverSynchronously(RestTemplate client, WebhookSubscription sub,
                                     String eventType, String body) {
        long timestamp = System.currentTimeMillis();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(EVENT_HEADER, eventType);
        headers.set(TIMESTAMP_HEADER, String.valueOf(timestamp));
        String signature = sign(sub.getSecretKey(), timestamp, body);
        if (signature != null) {
            headers.set(SIGNATURE_HEADER, signature);
        } else {
            log.warn("Webhook subscription {} has no secret key — sending UNSIGNED to {}",
                    sub.getId(), sub.getCallbackUrl());
        }
        client.exchange(sub.getCallbackUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    /**
     * Hex HMAC-SHA256 over {@code <timestamp>.<body>}, keyed with the subscription secret. Binding the
     * timestamp into the signed string stops an attacker replaying a captured body with a fresh
     * timestamp. Returns null when the subscription has no secret configured.
     */
    static String sign(String secretKey, long timestamp, String body) {
        if (secretKey == null || secretKey.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            log.error("Unable to compute webhook signature: {}", e.getMessage());
            return null;
        }
    }
}
