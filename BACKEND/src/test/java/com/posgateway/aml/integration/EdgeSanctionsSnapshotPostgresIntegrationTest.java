package com.posgateway.aml.integration;

import com.posgateway.aml.service.residency.EdgeSanctionsSnapshotService;
import com.posgateway.aml.service.residency.EdgeSanctionsSnapshotStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * V267 SQL against a real PostgreSQL 16: the snapshot store behind the Edge sanctions list (R10b) —
 * BUILDING → READY, chunk storage and retrieval by list hash, newest-READY ordering, pruning to two
 * snapshots, and the table constraints.
 *
 * <p>Database: {@code -Dschema.it.jdbc-url=… -Dschema.it.user=… -Dschema.it.password=…}, otherwise a
 * Testcontainers {@code postgres:16-alpine}; skipped when neither is available.
 */
class EdgeSanctionsSnapshotPostgresIntegrationTest {

    private static PostgreSQLContainer<?> container;
    private static JdbcTemplate jdbc;
    private EdgeSanctionsSnapshotStore store;

    /** Connects (explicit database or Testcontainers) and applies the Flyway history. */
    @BeforeAll
    @SuppressWarnings("resource")
    static void database() {
        String url = System.getProperty("schema.it.jdbc-url");
        String user = System.getProperty("schema.it.user", "postgres");
        String password = System.getProperty("schema.it.password", "");
        if (url == null || url.isBlank()) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    org.testcontainers.DockerClientFactory.instance().isDockerAvailable(),
                    "Docker not available to Testcontainers; pass -Dschema.it.jdbc-url to run against a database");
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            url = container.getJdbcUrl();
            user = container.getUsername();
            password = container.getPassword();
        }
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration")
                .placeholderReplacement(false).load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    }

    /** Stops the container if this class started one. */
    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    /** Each test starts with no snapshots. */
    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM edge_sanctions_snapshots");
        store = new EdgeSanctionsSnapshotStore(jdbc);
    }

    /** A builder whose clock is fixed at {@code at}. */
    private EdgeSanctionsSnapshotService service(Instant at) {
        return new EdgeSanctionsSnapshotService(store, mock(ApplicationEventPublisher.class), true,
                Clock.fixed(at, ZoneOffset.UTC));
    }

    /** Publishes {@code count} entities at {@code at}. */
    private EdgeSanctionsSnapshotStore.Snapshot publish(Instant at, int count, String prefix) {
        EdgeSanctionsSnapshotService.Builder builder = service(at).begin();
        for (int i = 0; i < count; i++) {
            Map<String, Object> e = new HashMap<>();
            e.put("entityId", prefix + i);
            e.put("name", "Name " + prefix + i);
            e.put("aliases", List.of());
            e.put("type", "ORGANIZATION");
            e.put("listName", "gb_hmt_sanctions");
            builder.add(e);
        }
        return builder.finish(null).orElseThrow();
    }

    @Test
    void aBuiltSnapshotIsServedByHashAndTheNewestIsLatest() {
        EdgeSanctionsSnapshotStore.Snapshot first = publish(Instant.parse("2026-10-15T02:00:00Z"), 6_000, "A");
        assertEquals(2, first.chunkCount());
        assertEquals(6_000, first.entryCount());
        List<EdgeSanctionsSnapshotStore.ChunkMeta> chunks = store.chunks(first.id());
        assertEquals(2, chunks.size());
        assertEquals(5_000, chunks.get(0).entryCount());
        assertTrue(chunks.get(0).bytes() > 0);
        EdgeSanctionsSnapshotStore.ChunkContent chunk = store.chunk(first.listHash(), 1).orElseThrow();
        assertEquals(1_000, chunk.entryCount());
        assertEquals(chunks.get(1).sha256(), chunk.sha256());
        assertTrue(store.chunk(first.listHash(), 2).isEmpty());

        EdgeSanctionsSnapshotStore.Snapshot second = publish(Instant.parse("2026-10-16T02:00:00Z"), 10, "B");
        assertEquals(second.listHash(), store.latestReady().orElseThrow().listHash());
        assertTrue(store.readyByHash(first.listHash()).isPresent(), "the previous snapshot is kept");

        publish(Instant.parse("2026-10-17T02:00:00Z"), 10, "C");
        assertTrue(store.readyByHash(first.listHash()).isEmpty(), "only two snapshots are kept");
        assertTrue(store.chunk(first.listHash(), 0).isEmpty(), "chunks go with their snapshot");
        assertTrue(store.readyByHash(second.listHash()).isPresent());
        assertTrue(store.pspsWithEdgeNodes() != null);
    }

    @Test
    void buildingRowsAreInvisibleAndConstraintsHold() {
        long building = store.createBuilding("building");
        store.insertChunk(building, 0, "a".repeat(64), 1, new byte[]{1});
        assertTrue(store.latestReady().isEmpty(), "a BUILDING snapshot is never served");
        assertThrows(DataIntegrityViolationException.class,
                () -> store.insertChunk(building, 0, "b".repeat(64), 1, new byte[]{2}), "one row per index");
        assertThrows(DataIntegrityViolationException.class,
                () -> store.insertChunk(building, 4096, "b".repeat(64), 1, new byte[]{2}), "index bound");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE edge_sanctions_snapshots SET status = 'READY' WHERE snapshot_id = ?", building),
                "READY needs a hash and a publish time");
        store.markReady(building, "v1", "c".repeat(64), Instant.parse("2026-10-17T00:00:00Z"), 1, 1, null);
        assertArrayEquals(new byte[]{1}, store.chunk("c".repeat(64), 0).orElseThrow().gzip());
        assertThrows(IllegalStateException.class,
                () -> store.markReady(building, "v1", "d".repeat(64), Instant.now(), 1, 1, null), "only once");
        long other = store.createBuilding("building");
        assertThrows(DataIntegrityViolationException.class,
                () -> store.markReady(other, "v2", "c".repeat(64), Instant.now(), 1, 1, null), "hashes are unique");
    }
}
