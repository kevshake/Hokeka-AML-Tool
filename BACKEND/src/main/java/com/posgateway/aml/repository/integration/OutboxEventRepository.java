package com.posgateway.aml.repository.integration;

import com.posgateway.aml.entity.integration.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    boolean existsByEventKey(String eventKey);

    @Query(value = """
            SELECT *
              FROM event_outbox
             WHERE status = 'PENDING'
               AND next_attempt_at <= CURRENT_TIMESTAMP
             ORDER BY created_at, id
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockReadyBatch(@Param("batchSize") int batchSize);
}
