package com.posgateway.aml.service.kafka;

import com.posgateway.aml.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumer for sending notifications (Email/SMS) based on Case events.
 * 
 * Future Integration: Brevo/SendGrid
 */
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    @KafkaListener(topics = KafkaConfig.TOPIC_CASE_LIFECYCLE, groupId = "notification-group")
    public void handleCaseLifecycleEvent(String message) {
        logger.info("Notification Service received Case Lifecycle Event: {}", message);
        // TODO: Parse JSON and send email to assigned user or group
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_CASE_DECISION, groupId = "notification-group")
    public void handleCaseDecisionEvent(String message) {
        logger.info("Notification Service received Case Decision Event: {}", message);
        // TODO: Send email to Compliance Officer or Merchant
    }
}
