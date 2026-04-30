package com.posgateway.aml.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.service.case_management.CaseCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumer for Real-Time Transaction Alerts from the Transaction Engine.
 * Topic: aml.transaction.alerts
 */
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionAlertConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TransactionAlertConsumer.class);

    private final CaseCreationService caseCreationService;
    private final ObjectMapper objectMapper;

    @Autowired
    public TransactionAlertConsumer(CaseCreationService caseCreationService, ObjectMapper objectMapper) {
        this.caseCreationService = caseCreationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "aml.transaction.alerts", groupId = "aml-case-group")
    public void consumeAlert(String message) {
        try {
            logger.info("Received transaction alert: {}", message);
            // Assuming message is a partial Transaction or Alert payload
            // For this implementation, we assume it's a TransactionEntity with context

            // In reality, this would likely be an AlertDTO.
            // Parsing as TransactionEntity for demonstration of flow.
            try {
                TransactionEntity tx = objectMapper.readValue(message, TransactionEntity.class);

                // Trigger logic - e.g. mapping external alert to internal case logic
                // If the message contains rule info, we can extract it.
                // Assuming generic high-risk alert for now.

                caseCreationService.triggerCaseFromRule(tx, "EXTERNAL_ENGINE_ALERT",
                        "Alert received from external transaction engine");

            } catch (Exception e) {
                logger.warn("Could not parse alert as TransactionEntity, treating as generic alert: {}",
                        e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error processing transaction alert", e);
        }
    }
}
