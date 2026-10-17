package com.hokeka.edge.sanctions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.ledger.EdgeLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The sanctions/PEP list in force on this node and its copy on the site ledger
 * ({@code edge_sanctions_lists}, {@code edge_sanctions_list_chunks}, ledger V3).
 *
 * <p>A list becomes the one in force only through {@link #activate}: every stored chunk is read back,
 * its SHA-256 and entry count checked against the stored manifest, the manifest's listHash re-derived,
 * and the in-memory {@link SanctionsIndex} built — then swapped in atomically. The same path restores
 * the list after a restart ({@link #restoreIfNeeded}), so the node screens without the Control Plane.
 * {@link #current()} never blocks and never touches the database: the evaluate path only reads the
 * swapped reference.
 */
public class SanctionsListStore {

    private static final Logger log = LoggerFactory.getLogger(SanctionsListStore.class);

    /** Lists kept on the ledger (the one in force and the previous one). */
    static final int KEEP_LISTS = 2;

    /**
     * The list in force.
     *
     * @param listHash    snapshot hash (matches Edge config {@code sanctions.listHash})
     * @param listVersion version label
     * @param publishedAt when the Control Plane published it
     * @param entryCount  entries
     * @param index       the in-memory index
     * @param activatedAt when this process activated it
     */
    public record Loaded(String listHash, String listVersion, Instant publishedAt, int entryCount, SanctionsIndex index,
                         Instant activatedAt) {
    }

    private final EdgeLedger ledger;
    private final ObjectMapper mapper;
    private final double similarityThreshold;
    private final Supplier<Optional<String>> preferredHash;
    private final AtomicReference<Loaded> current = new AtomicReference<>();
    private volatile boolean restored;
    private volatile String lastError = "";

    /**
     * @param ledger              the site ledger
     * @param mapper              JSON mapper
     * @param similarityThreshold matcher threshold
     * @param preferredHash       listHash of the Edge config in force (restore prefers that list)
     */
    public SanctionsListStore(EdgeLedger ledger, ObjectMapper mapper, double similarityThreshold,
                              Supplier<Optional<String>> preferredHash) {
        this.ledger = ledger;
        this.mapper = mapper;
        this.similarityThreshold = similarityThreshold;
        this.preferredHash = preferredHash;
    }

    /** The list in force (non-blocking). */
    public Optional<Loaded> current() {
        return Optional.ofNullable(current.get());
    }

    /** Hash of the list in force, or null. */
    public String currentHash() {
        Loaded l = current.get();
        return l == null ? null : l.listHash();
    }

    /** Log-safe reason of the last failure ({@code ""} after a success). */
    public String lastError() {
        return lastError;
    }

    /** The matcher threshold. */
    public double similarityThreshold() {
        return similarityThreshold;
    }

    /**
     * Restores the newest complete list from the ledger once (preferring the one the config names),
     * retried on later calls while the ledger is not ready. Called by a background schedule, never by
     * the evaluate path.
     */
    public synchronized void restoreIfNeeded() {
        if (restored || current.get() != null) {
            restored = true;
            return;
        }
        if (!ledger.ready() || ledger.dataSource() == null) {
            return;
        }
        try {
            String hash = null;
            Optional<String> preferred = preferredHash.get();
            if (preferred.isPresent() && isComplete(preferred.get())) {
                hash = preferred.get();
            }
            if (hash == null) {
                hash = newestComplete().orElse(null);
            }
            if (hash != null) {
                Loaded loaded = activate(hash, Instant.now());
                log.info("Sanctions list {} restored from the site ledger ({} entries)", loaded.listVersion(),
                        loaded.entryCount());
            }
            restored = true;
        } catch (SQLException | SanctionsListCodec.InvalidListException | RuntimeException e) {
            lastError = "restore failed (" + safe(e) + ")";
            log.warn("Could not restore the sanctions list from the ledger ({}); will retry", safe(e));
        }
    }

    /** Stores a verified manifest (idempotent: another node of the site may have stored it). */
    public void saveManifest(JsonNode manifest) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO edge_sanctions_lists (list_hash, list_version, published_at, entry_count, chunk_count, manifest)
                     VALUES (?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT (list_hash) DO NOTHING
                     """)) {
            ps.setString(1, manifest.get("listHash").asText());
            ps.setString(2, manifest.get("listVersion").asText());
            ps.setObject(3, OffsetDateTime.ofInstant(Instant.parse(manifest.get("publishedAt").asText()), ZoneOffset.UTC));
            ps.setInt(4, manifest.get("entryCount").asInt());
            ps.setInt(5, manifest.get("chunks").size());
            ps.setString(6, mapper.writeValueAsString(manifest));
            ps.executeUpdate();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new SQLException("manifest could not be serialised", "22000", e);
        }
    }

    /** Whether a chunk with this sha256 is already stored for the list. */
    public boolean hasChunk(String listHash, int index, String sha256) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM edge_sanctions_list_chunks WHERE list_hash = ? AND chunk_index = ? AND chunk_sha256 = ?")) {
            ps.setString(1, listHash);
            ps.setInt(2, index);
            ps.setString(3, sha256);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Stores one verified chunk as received (gzip). */
    public void saveChunk(String listHash, int index, String sha256, int entryCount, byte[] gzip) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO edge_sanctions_list_chunks (list_hash, chunk_index, chunk_sha256, entry_count, content_gzip)
                     VALUES (?, ?, ?, ?, ?) ON CONFLICT (list_hash, chunk_index) DO NOTHING
                     """)) {
            ps.setString(1, listHash);
            ps.setInt(2, index);
            ps.setString(3, sha256);
            ps.setInt(4, entryCount);
            ps.setBytes(5, gzip);
            ps.executeUpdate();
        }
    }

    /**
     * Re-verifies a stored list end to end, builds its index, marks it complete, swaps it in and prunes
     * older lists.
     *
     * @return the list now in force
     */
    public synchronized Loaded activate(String listHash, Instant now) throws SQLException,
            SanctionsListCodec.InvalidListException {
        JsonNode manifest;
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("SELECT manifest::text FROM edge_sanctions_lists WHERE list_hash = ?")) {
            ps.setString(1, listHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SanctionsListCodec.InvalidListException("list is not stored");
                }
                manifest = mapper.readTree(rs.getString(1));
            }
        } catch (java.io.IOException e) {
            throw new SanctionsListCodec.InvalidListException("stored manifest is not JSON");
        }
        SanctionsListCodec.verifyManifest(manifest);
        if (!listHash.equals(manifest.get("listHash").asText())) {
            throw new SanctionsListCodec.InvalidListException("stored manifest is for another list");
        }
        List<SanctionsEntry> entries = new ArrayList<>(manifest.get("entryCount").asInt());
        JsonNode chunks = manifest.get("chunks");
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT chunk_index, content_gzip FROM edge_sanctions_list_chunks WHERE list_hash = ? ORDER BY chunk_index")) {
            ps.setString(1, listHash);
            try (ResultSet rs = ps.executeQuery()) {
                int expected = 0;
                while (rs.next()) {
                    if (rs.getInt(1) != expected || expected >= chunks.size()) {
                        throw new SanctionsListCodec.InvalidListException("stored chunks do not match the manifest");
                    }
                    JsonNode line = chunks.get(expected);
                    entries.addAll(SanctionsListCodec.parseChunk(SanctionsListCodec.gunzip(rs.getBytes(2)),
                            line.get("sha256").asText(), line.get("entryCount").asInt(), mapper));
                    expected++;
                }
                if (expected != chunks.size()) {
                    throw new SanctionsListCodec.InvalidListException("list is incomplete (" + expected + " of "
                            + chunks.size() + " chunks)");
                }
            }
        }
        SanctionsIndex index = new SanctionsIndex(entries, similarityThreshold);
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE edge_sanctions_lists SET complete = true, completed_at = COALESCE(completed_at, ?) WHERE list_hash = ?")) {
            ps.setObject(1, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            ps.setString(2, listHash);
            ps.executeUpdate();
        }
        Loaded loaded = new Loaded(listHash, manifest.get("listVersion").asText(),
                Instant.parse(manifest.get("publishedAt").asText()), entries.size(), index, now);
        current.set(loaded);
        restored = true;
        lastError = "";
        prune(listHash);
        return loaded;
    }

    /** Records a fetch failure for status output. */
    void recordError(String reason) {
        lastError = reason;
    }

    /** Whether a list is stored complete. */
    private boolean isComplete(String listHash) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("SELECT complete FROM edge_sanctions_lists WHERE list_hash = ?")) {
            ps.setString(1, listHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /** Hash of the newest complete list. */
    private Optional<String> newestComplete() throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT list_hash FROM edge_sanctions_lists WHERE complete ORDER BY published_at DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(rs.getString(1).trim()) : Optional.empty();
        }
    }

    /** Keeps the list in force and the newest other one; deletes the rest (chunks cascade). */
    private void prune(String keep) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("""
                     DELETE FROM edge_sanctions_lists WHERE list_hash <> ? AND list_hash NOT IN (
                         SELECT list_hash FROM edge_sanctions_lists WHERE list_hash <> ?
                         ORDER BY published_at DESC LIMIT ?)
                     """)) {
            ps.setString(1, keep);
            ps.setString(2, keep);
            ps.setInt(3, KEEP_LISTS - 1);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Could not prune old sanctions lists ({})", safe(e));
        }
    }

    /** A ledger connection, or SQLException when the ledger is not ready. */
    private Connection connection() throws SQLException {
        if (!ledger.ready() || ledger.dataSource() == null) {
            throw new SQLException("Edge Ledger is not ready", "08000");
        }
        return ledger.dataSource().getConnection();
    }

    /** Log-safe message. */
    private static String safe(Throwable e) {
        return e instanceof SanctionsListCodec.InvalidListException ? e.getMessage() : EdgeLedger.safeMessage(e);
    }
}
