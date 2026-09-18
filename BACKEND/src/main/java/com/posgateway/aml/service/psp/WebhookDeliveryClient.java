package com.posgateway.aml.service.psp;

import com.posgateway.aml.entity.psp.WebhookSubscription;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Synchronous HTTP delivery used by {@link WebhookOutboxDispatcher}. HMAC signing matches
 * {@link WebhookService#SIGNATURE_HEADER} / {@link WebhookService#TIMESTAMP_HEADER}.
 */
@Component
public class WebhookDeliveryClient {

    private final RestTemplate restTemplate;
    private final RestTemplate vgsProxiedRestTemplate;

    @Value("${vgs.proxy.enabled:false}")
    private boolean vgsProxyEnabled;

    public WebhookDeliveryClient(@Qualifier("restTemplate") RestTemplate restTemplate,
                                 @Qualifier("vgsProxiedRestTemplate") RestTemplate vgsProxiedRestTemplate) {
        this.restTemplate = restTemplate;
        this.vgsProxiedRestTemplate = vgsProxiedRestTemplate;
    }

    public void deliver(WebhookSubscription subscription, String eventType, String body) {
        long timestamp = System.currentTimeMillis();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(WebhookService.EVENT_HEADER, eventType);
        headers.set(WebhookService.TIMESTAMP_HEADER, String.valueOf(timestamp));
        String signature = WebhookService.sign(subscription.getSecretKey(), timestamp, body);
        if (signature != null) {
            headers.set(WebhookService.SIGNATURE_HEADER, signature);
        }

        RestTemplate client = vgsProxyEnabled ? vgsProxiedRestTemplate : restTemplate;
        client.exchange(subscription.getCallbackUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }
}
