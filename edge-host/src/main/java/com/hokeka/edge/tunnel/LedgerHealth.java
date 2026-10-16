package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.ledger.EdgeLedger;
import com.hokeka.edge.ledger.LedgerProperties;
import com.hokeka.edge.residency.EdgeConfigDocument;
import com.hokeka.edge.residency.EdgeConfigStore;
import com.hokeka.edge.residency.LegalHoldService;
import com.hokeka.edge.residency.RetentionPolicy;
import com.hokeka.edge.residency.RetentionPurger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntSupplier;

/**
 * Builds the {@code ledger.health} item (EDGE_DATA_RESIDENCY_CONTRACT.md §7.6,
 * {@code ops/ledger.health.item.schema.json}): ledger status, schema version, row counts and range,
 * monthly partitions, outbox backlog, retention and last purge, holds with {@code activeHoldDigest},
 * storage and write failures. Counts and timestamps only, never row data.
 *
 * <p>{@code storage.diskFreeBytes} is the usable space of the file system holding this node's data
 * directory: PostgreSQL does not expose its own disk to an unprivileged role, and the node's disk
 * is the one an operator of a single-host site acts on.
 */
public final class LedgerHealth {

    private final EdgeLedger ledger;
    private final LegalHoldService holds;
    private final RetentionPurger purger;
    private final EdgeConfigStore config;
    private final LedgerProperties properties;
    private final RollingCount writeFailures;
    private final IntSupplier retryBacklog;
    private final Path dataDirectory;
    private final String siteId;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * @param ledger        the site ledger
     * @param holds         legal holds
     * @param purger        retention purge (last run, next run)
     * @param config        the config in force
     * @param properties    ledger settings (local retention floor)
     * @param writeFailures 24 h window over the recorder's write-failure counter
     * @param retryBacklog  evaluations waiting in the recorder's in-memory retry buffer
     * @param dataDirectory this node's data directory (for diskFreeBytes)
     * @param siteId        this node's site id
     * @param mapper        JSON mapper
     * @param clock         time source
     */
    public LedgerHealth(EdgeLedger ledger, LegalHoldService holds, RetentionPurger purger, EdgeConfigStore config,
                        LedgerProperties properties, RollingCount writeFailures, IntSupplier retryBacklog,
                        Path dataDirectory, String siteId, ObjectMapper mapper, Clock clock) {
        this.ledger = ledger;
        this.holds = holds;
        this.purger = purger;
        this.config = config;
        this.properties = properties;
        this.writeFailures = writeFailures;
        this.retryBacklog = retryBacklog;
        this.dataDirectory = dataDirectory;
        this.siteId = siteId;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** The 24 h write-failure window (sampled by a periodic task as well as by each health call). */
    public RollingCount writeFailures() {
        return writeFailures;
    }

    /**
     * Builds the item inside one budgeted read transaction.
     *
     * @throws TunnelException {@code LEDGER_UNAVAILABLE} when the ledger cannot be read
     */
    public ObjectNode item(LedgerReads reads, RequestControl control) throws TunnelException {
        Instant now = clock.instant();
        long failures = writeFailures.last24h();
        LegalHoldService.Summary holdSummary;
        try {
            holdSummary = holds.summary();
        } catch (SQLException e) {
            throw LedgerReads.map(e, control);
        }
        ObjectNode item = reads.read(control, s -> {
            ObjectNode n = mapper.createObjectNode();
            n.put("siteId", siteId);
            n.put("nodeId", ledger.nodeId());
            n.put("status", failures == 0 && retryBacklog.getAsInt() == 0 ? "OK" : "DEGRADED");
            n.put("schemaVersion", Math.max(1, ledger.schemaVersion()));
            ObjectNode rows = n.putObject("rows");
            ArrayNode partitions = mapper.createArrayNode();
            long approx = 0;
            try (java.sql.PreparedStatement ps = s.prepare(
                 """
                         SELECT child.relname, greatest(child.reltuples, 0)::bigint, pg_total_relation_size(child.oid)
                           FROM pg_inherits i
                           JOIN pg_class child ON child.oid = i.inhrelid
                           JOIN pg_class parent ON parent.oid = i.inhparent
                           JOIN pg_namespace ns ON ns.oid = parent.relnamespace
                          WHERE parent.relname = 'edge_evaluations' AND ns.nspname = current_schema()
                          ORDER BY child.relname DESC
                         """);
                              ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    approx += rs.getLong(2);
                    if (partitions.size() < 400 && name.matches("^edge_evaluations_\\d{4}_\\d{2}$")) {
                        ObjectNode p = partitions.addObject();
                        p.put("month", name.substring(17, 21) + "-" + name.substring(22, 24));
                        p.put("approxRows", rs.getLong(2));
                        p.put("bytes", rs.getLong(3));
                    }
                }
            }
            try (java.sql.PreparedStatement ps = s.prepare(
                 """
                         SELECT (SELECT count(*) FROM edge_held_evaluations),
                                (SELECT min(evaluated_at) FROM edge_all_evaluations),
                                (SELECT max(evaluated_at) FROM edge_all_evaluations),
                                pg_database_size(current_database())
                         """);
                              ResultSet rs = ps.executeQuery()) {
                rs.next();
                rows.put("approxCount", approx + rs.getLong(1));
                Instant oldest = LedgerReads.instant(rs, 2);
                Instant newest = LedgerReads.instant(rs, 3);
                if (oldest != null) {
                    rows.put("oldestEvaluatedAt", TunnelTime.seconds(oldest));
                }
                if (newest != null) {
                    rows.put("newestEvaluatedAt", TunnelTime.seconds(newest));
                }
                ObjectNode storage = n.putObject("storage");
                storage.put("dbSizeBytes", Math.max(0, rs.getLong(4)));
                storage.put("diskFreeBytes", diskFree());
            }
            n.set("partitions", partitions);
            ObjectNode outbox = n.putObject("outbox");
            try (java.sql.PreparedStatement ps = s.prepare(
                 """
                         SELECT (SELECT count(*) FROM edge_outbox WHERE shipped_at IS NULL),
                                (SELECT min(created_at) FROM edge_outbox WHERE shipped_at IS NULL),
                                (SELECT max(shipped_at) FROM edge_outbox WHERE shipped_at IS NOT NULL),
                                (SELECT count(*) FROM edge_outbox
                                  WHERE shipped_at IS NULL AND last_error IS NOT NULL
                                    AND last_attempt_at >= now() - interval '24 hours')
                         """);
                              ResultSet rs = ps.executeQuery()) {
                rs.next();
                outbox.put("backlog", rs.getLong(1));
                Instant oldestPending = LedgerReads.instant(rs, 2);
                Instant lastShipped = LedgerReads.instant(rs, 3);
                if (oldestPending != null) {
                    outbox.put("oldestPendingAt", TunnelTime.seconds(oldestPending));
                }
                if (lastShipped != null) {
                    outbox.put("lastShippedAt", TunnelTime.seconds(lastShipped));
                }
                outbox.put("failedAttempts24h", rs.getLong(4));
            }
            ObjectNode retention = n.putObject("retention");
            Optional<EdgeConfigDocument> doc = config.current();
            OptionalInt years = RetentionPolicy.effectiveYears(doc, properties.getRetentionMinYears());
            retention.put("years", years.orElse(RetentionPolicy.localFloor(properties.getRetentionMinYears())));
            doc.ifPresent(d -> retention.put("configVersion", d.configVersion()));
            try (java.sql.PreparedStatement ps = s.prepare(
                 """
                         SELECT run_at, cardinality(dropped_partitions) FROM edge_purge_log ORDER BY run_id DESC LIMIT 1
                         """);
                              ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    retention.put("lastPurgeAt", TunnelTime.seconds(LedgerReads.instant(rs, 1)));
                    retention.put("lastPurgeDroppedPartitions", rs.getLong(2));
                } else {
                    retention.put("lastPurgeDroppedPartitions", 0);
                }
            }
            retention.put("nextPurgeAt", TunnelTime.seconds(purger.nextPurgeAt(now)));
            return n;
        });
        ObjectNode h = item.putObject("holds");
        h.put("active", holdSummary.active());
        h.put("heldRows", holdSummary.heldRows());
        h.put("activeHoldDigest", holdSummary.activeHoldDigest());
        item.put("writeFailures24h", failures);
        return item;
    }

    /** Usable bytes on the data directory's file system (0 when it cannot be determined). */
    private long diskFree() {
        try {
            Path p = dataDirectory;
            while (p != null && !Files.exists(p)) {
                p = p.getParent();
            }
            return p == null ? 0 : Math.max(0, Files.getFileStore(p).getUsableSpace());
        } catch (IOException | SecurityException e) {
            return 0;
        }
    }
}
