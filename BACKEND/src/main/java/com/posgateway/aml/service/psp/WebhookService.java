package com.posgateway.aml.service.psp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Signing and header names for outbound webhooks.
 * Delivery itself is durable: emitters call {@link WebhookOutboxService}, and
 * {@link WebhookDeliveryClient} posts the signed body. There is no direct async send path.
 */
public final class WebhookService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebhookService.class);

    /** Header carrying the hex HMAC-SHA256 of {@code timestamp.body}. */
    static final String SIGNATURE_HEADER = "X-Hokeka-Signature";
    /** Header carrying the signing timestamp (epoch millis) so receivers can bound replay windows. */
    static final String TIMESTAMP_HEADER = "X-Hokeka-Timestamp";
    static final String EVENT_HEADER = "X-Hokeka-Event";

    private WebhookService() {}

    /**
     * Hex HMAC-SHA256 over {@code <timestamp>.<body>}, keyed with the subscription secret.
     * Returns null when the subscription has no secret configured.
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
