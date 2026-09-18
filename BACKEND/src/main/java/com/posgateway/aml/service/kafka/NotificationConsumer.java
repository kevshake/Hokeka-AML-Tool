package com.posgateway.aml.service.kafka;

import com.posgateway.aml.config.KafkaConfig;
import com.posgateway.aml.service.notification.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumer for sending email notifications based on Case events.
 *
 * <p>Real implementation: parses the Kafka payload and dispatches via
 * {@link EmailNotificationService} (Spring Mail, fail-soft).
 */
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    private final EmailNotificationService emailService;

    public NotificationConsumer(EmailNotificationService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_CASE_LIFECYCLE, groupId = "notification-group")
    public void handleCaseLifecycleEvent(String message) {
        logger.info("Notification consumer received Case Lifecycle Event");
        try {
            emailService.sendFromJson(message, "[Hokeka AML] Case lifecycle update");
        } catch (Exception ex) {
            logger.error("Failed to dispatch lifecycle email: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_CASE_DECISION, groupId = "notification-group")
    public void handleCaseDecisionEvent(String message) {
        logger.info("Notification consumer received Case Decision Event");
        try {
            emailService.sendFromJson(message, "[Hokeka AML] Case decision finalized");
        } catch (Exception ex) {
            logger.error("Failed to dispatch decision email: {}", ex.getMessage());
        }
    }
}
