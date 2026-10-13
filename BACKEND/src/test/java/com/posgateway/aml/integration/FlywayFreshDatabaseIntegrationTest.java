package com.posgateway.aml.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applies the full Flyway history on an empty Postgres instance (fresh install path).
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayFreshDatabaseIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migratesFromEmptyDatabaseThroughLatestVersion() throws Exception {
        Flyway flyway = productionAlignedFlyway(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var result = flyway.migrate();
        assertTrue(result.migrationsExecuted > 0, "expected Flyway to apply migrations");

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            try (ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
                assertTrue(rs.next(), "flyway_schema_history must record latest version");
                assertTrue(rs.getString(1).startsWith("V"), "latest migration must be versioned");
            }
        }
    }

    /**
     * Builds Flyway with the same placeholder policy as production
     * ({@code spring.flyway.placeholder-replacement=false} in application.properties).
     * V132 embeds Mustache-style {@code ${...}} in SAR JSON templates; enabling replacement
     * makes Flyway treat those as missing placeholders and abort fresh installs.
     */
    private static Flyway productionAlignedFlyway(String jdbcUrl, String user, String password) {
        return Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations("classpath:db/migration")
                .placeholderReplacement(false)
                .load();
    }
}
