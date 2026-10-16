package com.posgateway.aml.benchmark;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.AuditLogRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.pagination.KeysetSliceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Measures offset vs keyset list fetch times against a seeded Postgres when Docker is available.
 */
@Testcontainers
@SpringBootTest
class ConsoleListKeysetBenchmarkIT {

    private static final int SEED_ROWS = 2_000;

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

    /** Seeds sample rows and prints offset vs keyset timings to stdout for CI logs. */
    @Test
    void benchmarkOffsetVsKeysetAlertPaging() {
        seedAlerts();
        long alertOffsetMs = timeAlertOffsetPage();
        long alertKeysetMs = timeAlertKeysetWalk();
        System.out.printf(
                "BENCHMARK alerts count=%d offset_page50_ms=%d keyset_walk_ms=%d%n",
                alertRepository.count(),
                alertOffsetMs,
                alertKeysetMs);

        seedTransactions();
        long txnOffsetMs = timeTransactionOffsetPage();
        long txnKeysetMs = timeTransactionKeysetWalk();
        System.out.printf(
                "BENCHMARK transactions count=%d offset_page50_ms=%d keyset_walk_ms=%d%n",
                transactionRepository.count(),
                txnOffsetMs,
                txnKeysetMs);

        seedAuditLogs();
        long auditOffsetMs = timeAuditOffsetPage();
        long auditKeysetMs = timeAuditKeysetWalk();
        System.out.printf(
                "BENCHMARK audit count=%d offset_page50_ms=%d keyset_walk_ms=%d%n",
                auditLogRepository.count(),
                auditOffsetMs,
                auditKeysetMs);
    }

    /** Inserts alert rows used for paging benchmarks. */
    private void seedAlerts() {
        for (int i = 0; i < SEED_ROWS; i++) {
            Alert alert = new Alert();
            alert.setStatus("open");
            alert.setReason("bench-" + i);
            alert.setPspId(1L);
            alertRepository.save(alert);
        }
    }

    /** Inserts transaction rows used for paging benchmarks. */
    private void seedTransactions() {
        for (int i = 0; i < SEED_ROWS; i++) {
            TransactionEntity txn = new TransactionEntity();
            txn.setPspId(1L);
            txn.setMerchantId("1");
            txn.setAmountCents(100L + i);
            txn.setCurrency("USD");
            txn.setDecision("APPROVE");
            txn.setPanHash("bench-pan-" + i);
            txn.setTxnTs(java.time.LocalDateTime.now().minusMinutes(i));
            transactionRepository.save(txn);
        }
    }

    /** Inserts audit log rows used for paging benchmarks. */
    private void seedAuditLogs() {
        for (int i = 0; i < SEED_ROWS; i++) {
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
    }

    /** Times a single deep offset page fetch for alerts. */
    private long timeAlertOffsetPage() {
        Specification<Alert> spec = Specification.where(null);
        long offsetStart = System.nanoTime();
        alertRepository.findAll(spec, org.springframework.data.domain.PageRequest.of(50, 25));
        return (System.nanoTime() - offsetStart) / 1_000_000;
    }

    /** Times a keyset walk across alert pages. */
    private long timeAlertKeysetWalk() {
        Specification<Alert> spec = Specification.where(null);
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
        return (System.nanoTime() - keysetStart) / 1_000_000;
    }

    /** Times a single deep offset page fetch for transactions. */
    private long timeTransactionOffsetPage() {
        Specification<TransactionEntity> txnSpec = Specification.where(null);
        long txnOffsetStart = System.nanoTime();
        transactionRepository.findAll(txnSpec, org.springframework.data.domain.PageRequest.of(50, 25));
        return (System.nanoTime() - txnOffsetStart) / 1_000_000;
    }

    /** Times a keyset walk across transaction pages. */
    private long timeTransactionKeysetWalk() {
        Specification<TransactionEntity> txnSpec = Specification.where(null);
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
        return (System.nanoTime() - txnKeysetStart) / 1_000_000;
    }

    /** Times a single deep offset page fetch for audit logs. */
    private long timeAuditOffsetPage() {
        Specification<AuditLog> auditSpec = Specification.where(null);
        long auditOffsetStart = System.nanoTime();
        auditLogRepository.findAll(auditSpec, org.springframework.data.domain.PageRequest.of(50, 25));
        return (System.nanoTime() - auditOffsetStart) / 1_000_000;
    }

    /** Times a keyset walk across audit log pages. */
    private long timeAuditKeysetWalk() {
        Specification<AuditLog> auditSpec = Specification.where(null);
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
        return (System.nanoTime() - auditKeysetStart) / 1_000_000;
    }
}
