package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Write-time sealing and the backfill-aware chain link rule of {@link AuditLogService}. */
class AuditLogSealingTest {

    private AuditLogRepository repository;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"testenv"});
        service = new AuditLogService(repository, new ObjectMapper(), environment,
                mock(com.posgateway.aml.service.kafka.KafkaOutboxService.class));
        service.validateHmacKey();
        when(repository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sealAndSaveLinksToHeadTruncatesToMicrosAndChecksums() {
        AuditLog head = new AuditLog();
        head.setChecksum("head-checksum");
        when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.of(head));

        AuditLog row = AuditLog.builder().userId("u").username("u").actionType("CASE_DECISION_APPROVE")
                .entityType("CASE").entityId("9").timestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0, 123_456_789))
                .build();
        AuditLog saved = service.sealAndSave(row);

        assertThat(saved.getPreviousChecksum()).isEqualTo("head-checksum");
        assertThat(saved.getTimestamp().getNano()).isEqualTo(123_456_000);
        assertThat(saved.getChecksum()).isEqualTo(service.computeChecksum(saved));
    }

    @Test
    void sealAndSaveDefaultsMissingTimestamp() {
        when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        AuditLog row = AuditLog.builder().userId("u").username("u").actionType("LOGIN_FAILED")
                .entityType("USER").entityId("1").build();
        row.setTimestamp(null);
        AuditLog saved = service.sealAndSave(row);
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(saved.getTimestamp()).isEqualTo(saved.getTimestamp().truncatedTo(ChronoUnit.MICROS));
        assertThat(saved.getPreviousChecksum()).isNull();
    }

    @Test
    void linkRuleAcceptsPreservedLegacyChecksumOnlyForBackfilledPredecessor() {
        AuditLog predecessor = new AuditLog();
        predecessor.setChecksum("new");
        predecessor.setLegacyChecksum("old");
        AuditLog successor = new AuditLog();
        successor.setPreviousChecksum("old");

        assertThat(AuditLogService.linkMatches(successor, predecessor)).isFalse();
        predecessor.setChainBackfilledAt(LocalDateTime.now());
        assertThat(AuditLogService.linkMatches(successor, predecessor)).isTrue();

        successor.setPreviousChecksum("new");
        assertThat(AuditLogService.linkMatches(successor, predecessor)).isTrue();
        successor.setPreviousChecksum("other");
        assertThat(AuditLogService.linkMatches(successor, predecessor)).isFalse();

        AuditLog genesis = new AuditLog();
        assertThat(AuditLogService.linkMatches(genesis, null)).isTrue();
    }
}
