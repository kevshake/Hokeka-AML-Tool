package com.hokeka.edge.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

/**
 * Locates a real PostgreSQL 16 for the Edge Ledger integration tests.
 *
 * <ol>
 *   <li>{@code -Dledger.it.jdbc-url=jdbc:postgresql://127.0.0.1:55470/ledger} (plus
 *       {@code -Dledger.it.user} / {@code -Dledger.it.password}) — an explicit database; this is
 *       the path that works on hosts where Testcontainers cannot detect Docker.</li>
 *   <li>Otherwise Testcontainers {@code postgres:16-alpine}, when Docker is detectable.</li>
 *   <li>Otherwise the calling test is aborted (reported as skipped), never silently passed.</li>
 * </ol>
 *
 * <p>Every test gets its own schema ({@link #newSchema()}), so tests are isolated and can run
 * against a shared database; {@link #dropSchema} removes it afterwards.
 */
public final class LedgerTestDatabase {

    private static final String URL_PROPERTY = "ledger.it.jdbc-url";
    private static volatile PostgreSQLContainer<?> container;

    private LedgerTestDatabase() {
    }

    /** Connection coordinates of the test database. */
    public record Coordinates(String url, String user, String password) {
    }

    /** The database to use, or aborts the test when none is available. */
    public static synchronized Coordinates coordinates() {
        String url = System.getProperty(URL_PROPERTY, "").trim();
        if (!url.isEmpty()) {
            return new Coordinates(url, System.getProperty("ledger.it.user", "ledger"),
                    System.getProperty("ledger.it.password", "ledgerit"));
        }
        if (container == null) {
            boolean docker;
            try {
                docker = DockerClientFactory.instance().isDockerAvailable();
            } catch (Throwable t) {
                docker = false;
            }
            Assumptions.assumeTrue(docker, "No Edge Ledger database: set -D" + URL_PROPERTY
                    + "=jdbc:postgresql://host:port/db (Testcontainers cannot reach Docker here)");
            PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
            pg.start();
            container = pg;
        }
        return new Coordinates(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    /** A fresh, unique schema name for one test. */
    public static String newSchema() {
        return "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toLowerCase(Locale.ROOT);
    }

    /** Ledger properties pointing at the test database and the given schema. */
    public static LedgerProperties properties(String schema) {
        Coordinates c = coordinates();
        LedgerProperties p = new LedgerProperties();
        p.setUrl(c.url());
        p.setUsername(c.user());
        p.setPassword(c.password());
        p.setSchema(schema);
        p.setSiteId("acme-eu");
        p.setPoolSize(4);
        return p;
    }

    /** An initialised ledger over a fresh schema. */
    public static EdgeLedger openLedger(LedgerProperties properties, String nodeId) {
        EdgeLedger ledger = new EdgeLedger(properties, new ObjectMapper(), nodeId);
        if (!ledger.initialize()) {
            throw new IllegalStateException("test ledger did not initialise: " + ledger.stateReason());
        }
        return ledger;
    }

    /** A direct JDBC connection with the schema on the search path. */
    public static Connection connect(String schema) throws SQLException {
        Coordinates c = coordinates();
        Connection conn = DriverManager.getConnection(c.url(), c.user(), c.password());
        conn.setSchema(schema);
        return conn;
    }

    /** Drops a test schema and everything in it. */
    public static void dropSchema(String schema) {
        try (Connection conn = connect("public"); Statement st = conn.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        } catch (SQLException e) {
            throw new IllegalStateException("could not drop test schema " + schema, e);
        }
    }
}
