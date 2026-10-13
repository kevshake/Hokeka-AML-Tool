package com.posgateway.aml.benchmark;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.AuditLogRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.pagination.KeysetSliceService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Measures offset vs keyset alert list fetch times against a seeded Postgres when Docker is available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ConsoleListKeysetBenchmarkIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("hokeka_bench")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private KeysetSliceService keysetSliceService;

    /** Seeds sample alerts and prints offset vs keyset timings to stdout for CI logs. */
    @Test
    void benchmarkOffsetVsKeysetAlertPaging() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker required");
        Specification<Alert> spec = Specification.where(null);
        for (int i = 0; i < 2_000; i++) {
            Alert alert = new Alert();
            alert.setStatus("open");
            alert.setReason("bench-" + i);
            alert.setPspId(1L);
            alertRepository.save(alert);
        }
        long offsetStart = System.nanoTime();
        alertRepository.findAll(spec, org.springframework.data.domain.PageRequest.of(50, 25));
        long offsetMs = (System.nanoTime() - offsetStart) / 1_000_000;

        String cursor = null;
        long keysetStart = System.nanoTime();
        for (int page = 0; page < 50; page++) {
            var slice = keysetSliceService.slice(
                    alertRepository,
                    spec,
                    page,
                    25,
                    cursor,
                    "createdAt",
                    "alertId",
                    Alert::getCreatedAt,
                    Alert::getAlertId);
            cursor = slice.nextCursor();
            if (cursor == null) {
                break;
            }
        }
        long keysetMs = (System.nanoTime() - keysetStart) / 1_000_000;
        System.out.printf("BENCHMARK alerts offset_page50_ms=%d keyset_walk_ms=%d%n", offsetMs, keysetMs);

        for (int i = 0; i < 2_000; i++) {
            TransactionEntity txn = new TransactionEntity();
            txn.setPspId(1L);
            txn.setMerchantId("1");
            txn.setAmountCents(100L);
            txn.setCurrency("USD");
            txn.setDecision("APPROVE");
            txn.setTxnTs(java.time.LocalDateTime.now().minusMinutes(i));
            transactionRepository.save(txn);
        }
        Specification<TransactionEntity> txnSpec = Specification.where(null);
        long txnOffsetStart = System.nanoTime();
        transactionRepository.findAll(txnSpec, org.springframework.data.domain.PageRequest.of(50, 25));
        long txnOffsetMs = (System.nanoTime() - txnOffsetStart) / 1_000_000;
        String txnCursor = null;
        long txnKeysetStart = System.nanoTime();
        for (int page = 0; page < 50; page++) {
            var slice = keysetSliceService.slice(
                    transactionRepository,
                    txnSpec,
                    page,
                    25,
                    txnCursor,
                    "txnTs",
                    "txnId",
                    TransactionEntity::getTxnTs,
                    TransactionEntity::getTxnId);
            txnCursor = slice.nextCursor();
            if (txnCursor == null) {
                break;
            }
        }
        long txnKeysetMs = (System.nanoTime() - txnKeysetStart) / 1_000_000;
        System.out.printf("BENCHMARK transactions offset_page50_ms=%d keyset_walk_ms=%d%n", txnOffsetMs, txnKeysetMs);

        for (int i = 0; i < 2_000; i++) {
            AuditLog log = new AuditLog();
            log.setUserId("bench-user");
            log.setUsername("bench-user");
            log.setActionType("VIEW");
            log.setEntityType("TRANSACTION");
            log.setEntityId(String.valueOf(i));
            log.setPspId(1L);
            log.setSuccess(true);
            log.setTimestamp(java.time.LocalDateTime.now().minusMinutes(i));
            auditLogRepository.save(log);
        }
        Specification<AuditLog> auditSpec = Specification.where(null);
        long auditOffsetStart = System.nanoTime();
        auditLogRepository.findAll(auditSpec, org.springframework.data.domain.PageRequest.of(50, 25));
        long auditOffsetMs = (System.nanoTime() - auditOffsetStart) / 1_000_000;
        String auditCursor = null;
        long auditKeysetStart = System.nanoTime();
        for (int page = 0; page < 50; page++) {
            var slice = keysetSliceService.slice(
                    auditLogRepository,
                    auditSpec,
                    page,
                    25,
                    auditCursor,
                    "timestamp",
                    "id",
                    AuditLog::getTimestamp,
                    AuditLog::getId);
            auditCursor = slice.nextCursor();
            if (auditCursor == null) {
                break;
            }
        }
        long auditKeysetMs = (System.nanoTime() - auditKeysetStart) / 1_000_000;
        System.out.printf("BENCHMARK audit offset_page50_ms=%d keyset_walk_ms=%d%n", auditOffsetMs, auditKeysetMs);
    }
}
