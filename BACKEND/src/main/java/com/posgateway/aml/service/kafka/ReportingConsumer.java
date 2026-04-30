package com.posgateway.aml.service.kafka;

import com.posgateway.aml.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumer for Reporting and Analytics.
 * Updates real-time dashboards or data warehouse.
 */
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ReportingConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ReportingConsumer.class);

    @KafkaListener(topics = KafkaConfig.TOPIC_CASE_DECISION, groupId = "reporting-group")
    public void handleDecisionForReporting(String message) {
        logger.info("Reporting Service processed Decision: {}", message);
        // TODO: Update monthly report metrics or separate Analytics DB
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_COMPLIANCE_ALERT, groupId = "reporting-group")
    public void handleAlertForReporting(String message) {
        logger.info("Reporting Service processed Alert: {}", message);
    }
}
