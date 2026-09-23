package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditLogServiceIntegrityTest {

    private AuditLogRepository repository;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"testenv"});
        service = new AuditLogService(
                repository,
                new ObjectMapper(),
                environment,
                mock(com.posgateway.aml.service.kafka.KafkaOutboxService.class));
        service.validateHmacKey();
    }

    @Test
    void verifyIntegrityAcceptsValidChain() {
        AuditLog row = sample(1L, null, "aaa");
        when(repository.findTopByIdLessThanOrderByIdDesc(0L)).thenReturn(Optional.empty());
        when(repository.findByIdGreaterThanEqualOrderByIdAsc(eq(0L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        AuditLogService.AuditIntegrityReport report = service.verifyIntegrity(0L, 10);
        assertTrue(report.isValid());
    }

    @Test
    void verifyIntegrityDetectsTamperedChecksum() {
        AuditLog row = sample(2L, null, "wrong-checksum");
        when(repository.findTopByIdLessThanOrderByIdDesc(0L)).thenReturn(Optional.empty());
        when(repository.findByIdGreaterThanEqualOrderByIdAsc(eq(0L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        AuditLogService.AuditIntegrityReport report = service.verifyIntegrity(0L, 10);
        assertFalse(report.isValid());
        assertTrue(report.tamperedIds().contains(2L));
    }

    private AuditLog sample(Long id, String previous, String checksum) {
        AuditLog log = AuditLog.builder()
                .userId("u1")
                .username("user")
                .actionType("UPDATE")
                .entityType("CASE")
                .entityId("42")
                .timestamp(LocalDateTime.parse("2026-01-01T10:00:00"))
                .beforeValue("{}")
                .afterValue("{\"status\":\"OPEN\"}")
                .reason("test")
                .build();
        log.setId(id);
        log.setPreviousChecksum(previous);
        log.setChecksum(checksum != null && !checksum.equals("aaa")
                ? checksum
                : service.computeChecksum(log));
        return log;
    }
}
