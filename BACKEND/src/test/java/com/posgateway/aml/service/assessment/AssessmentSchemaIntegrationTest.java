package com.posgateway.aml.service.assessment;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applies {@code V238__assessments_findings.sql} on Postgres (Testcontainers) and verifies tenant-scoped rows.
 */
@Testcontainers(disabledWithoutDocker = true)
class AssessmentSchemaIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migrationSupportsAssessmentAndFindingInsert() throws Exception {
        String jdbcUrl = postgres.getJdbcUrl();
        String user = postgres.getUsername();
        String password = postgres.getPassword();

        String v119 = Files.readString(
                Path.of("src/main/resources/db/migration/V119__rule_execution_logs.sql"),
                StandardCharsets.UTF_8);
        String v238 = Files.readString(
                Path.of("src/main/resources/db/migration/V238__assessments_findings.sql"),
                StandardCharsets.UTF_8);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            conn.createStatement().execute(v119);
            conn.createStatement().execute(v238);

            UUID assessmentId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, txn_id, decision) "
                            + "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, assessmentId);
                ps.setLong(2, 42L);
                ps.setString(3, "TXN");
                ps.setString(4, "9001");
                ps.setLong(5, 9001L);
                ps.setString(6, "ALLOW");
                ps.executeUpdate();
            }

            UUID findingId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO findings (id, assessment_id, txn_id, source_type, triggered, phase, shadow) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, findingId);
                ps.setObject(2, assessmentId);
                ps.setLong(3, 9001L);
                ps.setString(4, "LIMIT");
                ps.setBoolean(5, false);
                ps.setString(6, "CP_SYNC");
                ps.setBoolean(7, true);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM findings f JOIN assessments a ON f.assessment_id = a.id "
                            + "WHERE a.psp_id = ? AND a.txn_id = ?")) {
                ps.setLong(1, 42L);
                ps.setLong(2, 9001L);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }
}
