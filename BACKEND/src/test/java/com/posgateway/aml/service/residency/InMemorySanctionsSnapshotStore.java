package com.posgateway.aml.service.residency;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test double of {@link EdgeSanctionsSnapshotStore} with the same semantics as its SQL (BUILDING /
 * READY rows, chunks per snapshot, newest-READY ordering, pruning), so the snapshot builder, list
 * service and controller can be exercised end to end without a database. The SQL itself is covered
 * by {@code EdgeSanctionsSnapshotPostgresIntegrationTest}.
 */
public class InMemorySanctionsSnapshotStore extends EdgeSanctionsSnapshotStore {

    /** One stored snapshot row. */
    static final class Row {
        long id;
        String listVersion;
        String listHash;
        Instant publishedAt;
        int entryCount;
        int chunkCount;
        String status = BUILDING;
        final TreeMap<Integer, ChunkContent> chunks = new TreeMap<>();
    }

    final Map<Long, Row> rows = new LinkedHashMap<>();
    final List<Long> pspIds = new ArrayList<>();
    /** When set, the n-th insertChunk call (1-based) throws, to exercise the abandon path. */
    int failOnChunk = -1;
    private int chunkCalls;
    private final AtomicLong ids = new AtomicLong();

    public InMemorySanctionsSnapshotStore() {
        super(null);
    }

    /** Creates a BUILDING row. */
    @Override
    public long createBuilding(String listVersion) {
        Row row = new Row();
        row.id = ids.incrementAndGet();
        row.listVersion = listVersion;
        rows.put(row.id, row);
        return row.id;
    }

    /** Stores a chunk (or fails on the configured call). */
    @Override
    public void insertChunk(long snapshotId, int index, String sha256, int entryCount, byte[] gzip) {
        chunkCalls++;
        if (chunkCalls == failOnChunk) {
            throw new IllegalStateException("simulated chunk insert failure");
        }
        rows.get(snapshotId).chunks.put(index, new ChunkContent(null, index, sha256, entryCount, gzip));
    }

    /** Chunk manifest lines in index order. */
    @Override
    public List<ChunkMeta> chunks(long snapshotId) {
        List<ChunkMeta> out = new ArrayList<>();
        for (ChunkContent c : rows.get(snapshotId).chunks.values()) {
            out.add(new ChunkMeta(c.index(), c.sha256(), c.entryCount(), c.gzip().length));
        }
        return out;
    }

    /** BUILDING → READY. */
    @Override
    public void markReady(long snapshotId, String listVersion, String listHash, Instant publishedAt, int entryCount,
                          int chunkCount, Integer sanctionsListId) {
        Row row = rows.get(snapshotId);
        if (row == null || !BUILDING.equals(row.status)) {
            throw new IllegalStateException("snapshot " + snapshotId + " is not BUILDING");
        }
        row.status = READY;
        row.listVersion = listVersion;
        row.listHash = listHash;
        row.publishedAt = publishedAt;
        row.entryCount = entryCount;
        row.chunkCount = chunkCount;
    }

    /** Deletes a row. */
    @Override
    public void delete(long snapshotId) {
        rows.remove(snapshotId);
    }

    /** Newest READY row. */
    @Override
    public Optional<Snapshot> latestReady() {
        return rows.values().stream().filter(r -> READY.equals(r.status))
                .max(Comparator.comparing((Row r) -> r.publishedAt).thenComparingLong(r -> r.id))
                .map(InMemorySanctionsSnapshotStore::snapshot);
    }

    /** READY row by hash. */
    @Override
    public Optional<Snapshot> readyByHash(String listHash) {
        return rows.values().stream().filter(r -> READY.equals(r.status) && listHash.equals(r.listHash)).findFirst()
                .map(InMemorySanctionsSnapshotStore::snapshot);
    }

    /** A chunk of a READY row. */
    @Override
    public Optional<ChunkContent> chunk(String listHash, int index) {
        return rows.values().stream().filter(r -> READY.equals(r.status) && listHash.equals(r.listHash)).findFirst()
                .map(r -> r.chunks.get(index))
                .map(c -> new ChunkContent(listHash, c.index(), c.sha256(), c.entryCount(), c.gzip()));
    }

    /** Keeps the newest {@code keep} READY rows. */
    @Override
    public int prune(int keep) {
        List<Row> ready = rows.values().stream().filter(r -> READY.equals(r.status))
                .sorted(Comparator.comparing((Row r) -> r.publishedAt).thenComparingLong(r -> r.id).reversed())
                .toList();
        int dropped = 0;
        for (int i = Math.max(1, keep); i < ready.size(); i++) {
            rows.remove(ready.get(i).id);
            dropped++;
        }
        return dropped;
    }

    /** Configured PSP ids. */
    @Override
    public List<Long> pspsWithEdgeNodes() {
        return List.copyOf(pspIds);
    }

    /** Row → record. */
    private static Snapshot snapshot(Row r) {
        return new Snapshot(r.id, r.listVersion, r.listHash, r.publishedAt, r.entryCount, r.chunkCount);
    }
}
