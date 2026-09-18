package com.posgateway.aml.service.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to send notifications to Slack via Webhooks
 */
@Service
public class SlackService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SlackService.class);

    @Value("${slack.webhook.url:}")
    private String webhookUrl;

    @Value("${slack.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate;

    public SlackService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendAlert(String message, String severity) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            log.debug("Slack notifications disabled or webhook URL not set. Skipping message: {}", message);
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            String iconEmoji = switch (severity.toUpperCase()) {
                case "CRITICAL" -> "🔴";
                case "HIGH" -> "🟠";
                case "WARN" -> "⚠️";
                default -> "ℹ️";
            };

            payload.put("text", iconEmoji + " *[" + severity + "]* " + message);

            restTemplate.postForEntity(webhookUrl, payload, String.class);
            log.info("Sent Slack alert: {}", message);

        } catch (Exception e) {
            log.error("Failed to send Slack alert: {}", e.getMessage());
        }
    }
}
