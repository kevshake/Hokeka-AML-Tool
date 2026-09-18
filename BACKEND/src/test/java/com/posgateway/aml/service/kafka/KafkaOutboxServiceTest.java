package com.posgateway.aml.service.kafka;

import com.posgateway.aml.entity.integration.OutboxEvent;
import com.posgateway.aml.repository.integration.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaOutboxServiceTest {

    @Test
    void enqueuesNewEventWithStableIdentity() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.existsByEventKey("alert.generated:7")).thenReturn(false);
        KafkaOutboxService service = new KafkaOutboxService(repository);

        service.enqueue("alert.generated:7", "alerts.generated", "4", "{\"alertId\":7}");

        verify(repository).save(argThat(event ->
                "alert.generated:7".equals(event.getEventKey())
                        && "alerts.generated".equals(event.getTopic())
                        && "4".equals(event.getPartitionKey())
                        && event.getStatus() == OutboxEvent.Status.PENDING));
    }

    @Test
    void duplicateEventKeyIsIdempotent() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.existsByEventKey("transaction.raw:9")).thenReturn(true);
        KafkaOutboxService service = new KafkaOutboxService(repository);

        service.enqueue("transaction.raw:9", "transactions.raw", "2", "{}");

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
