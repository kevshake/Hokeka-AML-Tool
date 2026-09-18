package com.posgateway.aml.service.kafka;

import com.posgateway.aml.entity.integration.OutboxEvent;
import com.posgateway.aml.repository.integration.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaOutboxDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaOutboxDispatcher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final long sendTimeoutSeconds;

    public KafkaOutboxDispatcher(OutboxEventRepository repository,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 @Value("${kafka.outbox.batch-size:100}") int batchSize,
                                 @Value("${kafka.outbox.send-timeout-seconds:10}") long sendTimeoutSeconds) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${kafka.outbox.dispatch-delay-ms:1000}")
    @Transactional
    public void dispatchReadyEvents() {
        List<OutboxEvent> events = repository.lockReadyBatch(batchSize);
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                        .get(sendTimeoutSeconds, TimeUnit.SECONDS);
                event.setStatus(OutboxEvent.Status.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                event.setLastError(null);
            } catch (Exception failure) {
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(limit(failure.getMessage(), 4000));
                long retrySeconds = Math.min(300L, 1L << Math.min(attempts, 8));
                event.setNextAttemptAt(LocalDateTime.now().plusSeconds(retrySeconds));
                logger.warn("Kafka outbox delivery failed: id={} topic={} attempt={} retrySeconds={} error={}",
                        event.getId(), event.getTopic(), attempts, retrySeconds, failure.getMessage());
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
