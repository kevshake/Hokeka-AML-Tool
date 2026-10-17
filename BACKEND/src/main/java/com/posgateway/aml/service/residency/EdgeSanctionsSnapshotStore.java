package com.posgateway.aml.service.residency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence of the Edge sanctions/PEP list snapshots ({@code edge_sanctions_snapshots} and
 * {@code edge_sanctions_snapshot_chunks}, V267; contract section 6.4). Plain JDBC: the chunks are
 * opaque gzip blobs that are written once and served as they are.
 */
@Component
public class EdgeSanctionsSnapshotStore {

    /** Status of a snapshot whose chunks are still being written. */
    static final String BUILDING = "BUILDING";
    /** Status of a complete, published snapshot. */
    static final String READY = "READY";

    private final JdbcTemplate jdbc;

    public EdgeSanctionsSnapshotStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * A published snapshot.
     *
     * @param id          row id
     * @param listVersion schema-safe version label (edge-config {@code sanctions.listVersion})
     * @param listHash    SHA-256 hex over the chunk lines (edge-config {@code sanctions.listHash})
     * @param publishedAt when it became READY
     * @param entryCount  entries over all chunks
     * @param chunkCount  number of chunks
     */
    public record Snapshot(long id, String listVersion, String listHash, Instant publishedAt, int entryCount,
                           int chunkCount) {
    }

    /**
     * Manifest line of one chunk.
     *
     * @param index      0-based chunk index
     * @param sha256     SHA-256 hex of the uncompressed JSON lines
     * @param entryCount entries in the chunk
     * @param bytes      size of the gzip payload
     */
    public record ChunkMeta(int index, String sha256, int entryCount, int bytes) {
    }

    /**
     * One chunk with its payload.
     *
     * @param listHash   the snapshot it belongs to
     * @param index      0-based chunk index
     * @param sha256     SHA-256 hex of the uncompressed JSON lines
     * @param entryCount entries in the chunk
     * @param gzip       gzip-compressed JSON lines
     */
    public record ChunkContent(String listHash, int index, String sha256, int entryCount, byte[] gzip) {
    }

    /** Inserts a BUILDING snapshot and returns its id. */
    public long createBuilding(String listVersion) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO edge_sanctions_snapshots (list_version, status) VALUES (?, 'BUILDING')",
                    new String[]{"snapshot_id"});
            ps.setString(1, listVersion);
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("edge_sanctions_snapshots insert returned no id");
        }
        return id.longValue();
    }

    /** Stores one chunk of a BUILDING snapshot. */
    public void insertChunk(long snapshotId, int index, String sha256, int entryCount, byte[] gzip) {
        jdbc.update("INSERT INTO edge_sanctions_snapshot_chunks (snapshot_id, chunk_index, chunk_sha256, entry_count, "
                + "content_gzip) VALUES (?, ?, ?, ?, ?)", snapshotId, index, sha256, entryCount, gzip);
    }

    /** Chunk manifest lines of a snapshot, in index order. */
    public List<ChunkMeta> chunks(long snapshotId) {
        return jdbc.query("SELECT chunk_index, chunk_sha256, entry_count, octet_length(content_gzip) "
                        + "FROM edge_sanctions_snapshot_chunks WHERE snapshot_id = ? ORDER BY chunk_index",
                (rs, i) -> new ChunkMeta(rs.getInt(1), rs.getString(2).trim(), rs.getInt(3), rs.getInt(4)),
                snapshotId);
    }

    /** Marks a BUILDING snapshot READY with its identity. */
    public void markReady(long snapshotId, String listVersion, String listHash, Instant publishedAt, int entryCount,
                          int chunkCount, Integer sanctionsListId) {
        int updated = jdbc.update("UPDATE edge_sanctions_snapshots SET status = 'READY', list_version = ?, list_hash = ?, "
                        + "published_at = ?, entry_count = ?, chunk_count = ?, sanctions_list_id = ? "
                        + "WHERE snapshot_id = ? AND status = 'BUILDING'",
                listVersion, listHash, Timestamp.from(publishedAt), entryCount, chunkCount, sanctionsListId, snapshotId);
        if (updated != 1) {
            throw new IllegalStateException("snapshot " + snapshotId + " is not BUILDING");
        }
    }

    /** Deletes a snapshot and its chunks. */
    public void delete(long snapshotId) {
        jdbc.update("DELETE FROM edge_sanctions_snapshots WHERE snapshot_id = ?", snapshotId);
    }

    /** The newest READY snapshot. */
    public Optional<Snapshot> latestReady() {
        return jdbc.query("SELECT snapshot_id, list_version, list_hash, published_at, entry_count, chunk_count "
                        + "FROM edge_sanctions_snapshots WHERE status = 'READY' "
                        + "ORDER BY published_at DESC, snapshot_id DESC LIMIT 1",
                (rs, i) -> snapshot(rs)).stream().findFirst();
    }

    /** The READY snapshot with this hash. */
    public Optional<Snapshot> readyByHash(String listHash) {
        return jdbc.query("SELECT snapshot_id, list_version, list_hash, published_at, entry_count, chunk_count "
                        + "FROM edge_sanctions_snapshots WHERE status = 'READY' AND list_hash = ?",
                (rs, i) -> snapshot(rs), listHash).stream().findFirst();
    }

    /** One chunk of a READY snapshot, addressed by the snapshot's hash. */
    public Optional<ChunkContent> chunk(String listHash, int index) {
        return jdbc.query("SELECT s.list_hash, c.chunk_index, c.chunk_sha256, c.entry_count, c.content_gzip "
                        + "FROM edge_sanctions_snapshot_chunks c JOIN edge_sanctions_snapshots s "
                        + "ON s.snapshot_id = c.snapshot_id WHERE s.status = 'READY' AND s.list_hash = ? AND c.chunk_index = ?",
                (rs, i) -> new ChunkContent(rs.getString(1).trim(), rs.getInt(2), rs.getString(3).trim(), rs.getInt(4),
                        rs.getBytes(5)),
                listHash, index).stream().findFirst();
    }

    /**
     * Keeps the {@code keep} newest READY snapshots (an Edge mid-download of the previous one can still
     * finish it) and drops BUILDING leftovers of interrupted builds older than a day.
     *
     * @return rows deleted
     */
    public int prune(int keep) {
        int dropped = jdbc.update("DELETE FROM edge_sanctions_snapshots WHERE status = 'READY' AND snapshot_id NOT IN "
                + "(SELECT snapshot_id FROM edge_sanctions_snapshots WHERE status = 'READY' "
                + "ORDER BY published_at DESC, snapshot_id DESC LIMIT ?)", Math.max(1, keep));
        dropped += jdbc.update("DELETE FROM edge_sanctions_snapshots WHERE status = 'BUILDING' "
                + "AND created_at < NOW() - INTERVAL '1 day'");
        return dropped;
    }

    /** PSPs that have at least one Edge Node (they receive a new config version on publish). */
    public List<Long> pspsWithEdgeNodes() {
        return jdbc.queryForList("SELECT DISTINCT psp_id FROM edge_nodes WHERE psp_id IS NOT NULL ORDER BY psp_id",
                Long.class);
    }

    /** Maps one snapshot row. */
    private static Snapshot snapshot(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp published = rs.getTimestamp(4);
        return new Snapshot(rs.getLong(1), rs.getString(2), rs.getString(3).trim(),
                published == null ? Instant.EPOCH : published.toInstant(), rs.getInt(5), rs.getInt(6));
    }
}
