package com.posgateway.aml.service.kafka;

import com.posgateway.aml.entity.integration.OutboxEvent;
import com.posgateway.aml.repository.integration.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KafkaOutboxService {

    private final OutboxEventRepository repository;

    public KafkaOutboxService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void enqueue(String eventKey, String topic, String partitionKey, String payload) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("eventKey is required");
        }
        if (repository.existsByEventKey(eventKey)) {
            return;
        }

        OutboxEvent event = new OutboxEvent();
        event.setEventKey(eventKey);
        event.setTopic(topic);
        event.setPartitionKey(partitionKey);
        event.setPayload(payload);
        repository.save(event);
    }
}
