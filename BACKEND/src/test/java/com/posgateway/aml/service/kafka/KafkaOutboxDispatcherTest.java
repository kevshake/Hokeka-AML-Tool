package com.posgateway.aml.service.kafka;

import com.posgateway.aml.entity.integration.OutboxEvent;
import com.posgateway.aml.repository.integration.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaOutboxDispatcherTest {

    @Test
    void marksAcknowledgedEventPublished() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        OutboxEvent event = event();
        when(repository.lockReadyBatch(10)).thenReturn(List.of(event));
        when(kafkaTemplate.send("alerts.generated", "3", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        new KafkaOutboxDispatcher(repository, kafkaTemplate, 10, 1).dispatchReadyEvents();

        assertEquals(OutboxEvent.Status.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
    }

    @Test
    void schedulesRetryWhenKafkaDoesNotAcknowledge() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        OutboxEvent event = event();
        LocalDateTime before = LocalDateTime.now();
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failed =
                new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(repository.lockReadyBatch(10)).thenReturn(List.of(event));
        when(kafkaTemplate.send("alerts.generated", "3", "{}")).thenReturn(failed);

        new KafkaOutboxDispatcher(repository, kafkaTemplate, 10, 1).dispatchReadyEvents();

        assertEquals(OutboxEvent.Status.PENDING, event.getStatus());
        assertEquals(1, event.getAttempts());
        assertTrue(event.getNextAttemptAt().isAfter(before));
        assertTrue(event.getLastError().contains("broker unavailable"));
    }

    private OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setEventKey("alert.generated:7");
        event.setTopic("alerts.generated");
        event.setPartitionKey("3");
        event.setPayload("{}");
        return event;
    }
}
