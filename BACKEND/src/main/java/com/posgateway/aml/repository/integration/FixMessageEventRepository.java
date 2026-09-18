package com.posgateway.aml.repository.integration;

import com.posgateway.aml.entity.integration.FixMessageEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FixMessageEventRepository extends JpaRepository<FixMessageEvent, Long> {
    Page<FixMessageEvent> findByPspIdOrderByReceivedAtDesc(Long pspId, Pageable pageable);
    Optional<FixMessageEvent> findByIdAndPspId(Long id, Long pspId);
    Optional<FixMessageEvent> findBySessionIdAndMessageSequenceNumberAndDirection(
            String sessionId, int messageSequenceNumber, String direction);
}
