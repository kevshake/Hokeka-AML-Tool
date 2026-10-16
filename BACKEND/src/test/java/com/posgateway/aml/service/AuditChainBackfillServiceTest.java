package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.repository.AuditLogRepository;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the legacy audit hash-chain backfill against a real (H2, PostgreSQL-mode) table,
 * then proves the resulting chain passes {@link AuditLogService#verifyIntegrity}.
 */
class AuditChainBackfillServiceTest {

    private static final String KEY = "unit-test-audit-hmac-key-0123456789";

    private JdbcTemplate jdbc;
    private AuditLogService auditLogService;
    private AuditChainBackfillService backfill;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:audit" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                        + "NON_KEYWORDS=TIMESTAMP,VALUE", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE audit_logs_enhanced (id BIGINT PRIMARY KEY, user_id VARCHAR(255), "
                + "username VARCHAR(255), action_type VARCHAR(100), entity_type VARCHAR(100), "
                + "entity_id VARCHAR(255), before_value TEXT, after_value TEXT, timestamp TIMESTAMP, "
                + "reason TEXT, checksum VARCHAR(255), previous_checksum VARCHAR(128), "
                + "legacy_checksum VARCHAR(255), chain_backfilled_at TIMESTAMP)");

        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"testenv"});
        auditLogService = new AuditLogService(mock(AuditLogRepository.class), new ObjectMapper(), environment,
                mock(com.posgateway.aml.service.kafka.KafkaOutboxService.class));
        ReflectionTestUtils.setField(auditLogService, "hmacKey", KEY);
        backfill = new AuditChainBackfillService(jdbc, auditLogService, true);
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP ALL OBJECTS");
    }

    private static LocalDateTime ts(int minute) {
        return LocalDateTime.of(2025, 5, 1, 9, minute, 0, 123_456_000);
    }

    /** Inserts a raw row exactly as a legacy/unsealed writer would have left it. */
    private void insert(long id, String entityId, LocalDateTime when, String checksum, String previous) {
        jdbc.update("INSERT INTO audit_logs_enhanced (id, user_id, username, action_type, entity_type, entity_id, "
                        + "before_value, after_value, timestamp, reason, checksum, previous_checksum) "
                        + "VALUES (?, 'u1', 'user', 'UPDATE', 'CASE', ?, '{}', '{\"s\":1}', ?, 'r', ?, ?)",
                id, entityId, Timestamp.valueOf(when), checksum, previous);
    }

    /** Pre-V232 checksum format, as the old AuditLogService computed it. */
    private static String legacyChecksum(String entityId, LocalDateTime when) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, KEY).hmacHex("u1" + "UPDATE" + "CASE" + entityId + when);
    }

    /** Current-format sealed checksum for a row with the given link. */
    private String sealedChecksum(String entityId, LocalDateTime when, String previous) {
        AuditLog row = new AuditLog();
        row.setUserId("u1");
        row.setActionType("UPDATE");
        row.setEntityType("CASE");
        row.setEntityId(entityId);
        row.setTimestamp(when);
        row.setBeforeValue("{}");
        row.setAfterValue("{\"s\":1}");
        row.setReason("r");
        row.setPreviousChecksum(previous);
        return auditLogService.computeChecksum(row);
    }

    @Test
    void sealsLegacyAndUnsealedRowsKeepsLaterChainValidAndLeavesForgedRowAlone() {
        // 1-2: pre-V232 rows with old-format checksums, no links.
        String legacy1 = legacyChecksum("1", ts(1));
        String legacy2 = legacyChecksum("2", ts(2));
        insert(1, "1", ts(1), legacy1, null);
        insert(2, "2", ts(2), legacy2, null);
        // 3: first chained row, written while row 2 still had its legacy checksum.
        String sealed3 = sealedChecksum("3", ts(3), legacy2);
        insert(3, "3", ts(3), sealed3, legacy2);
        // 4: written by a path that bypassed sealing (no checksum, no link).
        insert(4, "4", ts(4), null, null);
        // 5: sealed at write time while row 4 had no checksum -> link is null.
        String sealed5 = sealedChecksum("5", ts(5), null);
        insert(5, "5", ts(5), sealed5, null);
        // 6: a legacy-looking row whose checksum matches neither format (possible tampering).
        insert(6, "6", ts(6), "deadbeef", null);

        AuditChainBackfillService.BackfillResult result = backfill.backfill();

        assertThat(result.sealed()).isEqualTo(3);          // rows 1, 2, 4
        assertThat(result.alreadySealed()).isEqualTo(1);   // row 5
        assertThat(result.unverifiableIds()).containsExactly(6L);

        Map<String, Object> row1 = jdbc.queryForMap("SELECT * FROM audit_logs_enhanced WHERE id = 1");
        assertThat(row1.get("legacy_checksum")).isEqualTo(legacy1);
        assertThat(row1.get("previous_checksum")).isNull();
        assertThat(row1.get("chain_backfilled_at")).isNotNull();
        Map<String, Object> row2 = jdbc.queryForMap("SELECT * FROM audit_logs_enhanced WHERE id = 2");
        assertThat(row2.get("previous_checksum")).isEqualTo(row1.get("checksum"));
        assertThat(jdbc.queryForObject("SELECT checksum FROM audit_logs_enhanced WHERE id = 3", String.class))
                .isEqualTo(sealed3);
        assertThat(jdbc.queryForObject("SELECT checksum FROM audit_logs_enhanced WHERE id = 6", String.class))
                .isEqualTo("deadbeef");

        // Second run is a no-op (idempotent).
        AuditChainBackfillService.BackfillResult again = backfill.backfill();
        assertThat(again.sealed()).isZero();

        // Whole chain verifies except the forged row (and nothing else).
        assertThat(verify()).containsExactly(6L);
    }

    @Test
    void rowWithoutAnyChecksumBecomesVerifiable() {
        insert(10, "10", ts(10), null, null);
        assertThat(backfill.backfill().sealed()).isEqualTo(1);
        assertThat(verify()).isEmpty();
    }

    /** Loads every row as an entity and runs the service's integrity verification over them. */
    private List<Long> verify() {
        List<AuditLog> rows = jdbc.query("SELECT * FROM audit_logs_enhanced ORDER BY id", (rs, i) -> {
            AuditLog row = new AuditLog();
            row.setId(rs.getLong("id"));
            row.setUserId(rs.getString("user_id"));
            row.setActionType(rs.getString("action_type"));
            row.setEntityType(rs.getString("entity_type"));
            row.setEntityId(rs.getString("entity_id"));
            row.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
            row.setBeforeValue(rs.getString("before_value"));
            row.setAfterValue(rs.getString("after_value"));
            row.setReason(rs.getString("reason"));
            row.setChecksum(rs.getString("checksum"));
            row.setPreviousChecksum(rs.getString("previous_checksum"));
            row.setLegacyChecksum(rs.getString("legacy_checksum"));
            Timestamp backfilled = rs.getTimestamp("chain_backfilled_at");
            row.setChainBackfilledAt(backfilled == null ? null : backfilled.toLocalDateTime());
            return row;
        });
        AuditLogRepository repo = mock(AuditLogRepository.class);
        when(repo.findTopByIdLessThanOrderByIdDesc(anyLong())).thenReturn(Optional.empty());
        when(repo.findByIdGreaterThanEqualOrderByIdAsc(eq(0L), any(Pageable.class))).thenReturn(new PageImpl<>(rows));
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"testenv"});
        AuditLogService verifier = new AuditLogService(repo, new ObjectMapper(), environment,
                mock(com.posgateway.aml.service.kafka.KafkaOutboxService.class));
        ReflectionTestUtils.setField(verifier, "hmacKey", KEY);
        return verifier.verifyIntegrity(0L, 1000).tamperedIds();
    }
}
