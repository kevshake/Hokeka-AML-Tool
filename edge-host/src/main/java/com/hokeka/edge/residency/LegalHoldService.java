package com.hokeka.edge.residency;

import com.hokeka.edge.contract.Jcs;
import com.hokeka.edge.ledger.EdgeLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Legal holds on the Edge (EDGE_DATA_RESIDENCY_CONTRACT.md §7.10, §8.3). Holds arrive only as
 * tunnel {@code notify hold.place} / {@code hold.release} frames and are acknowledged with
 * {@code hold.ack}; the cloud is their source of truth and re-sends unacknowledged ones after every
 * {@code hello.ack}, so both operations are idempotent on {@code holdId}.
 *
 * <ul>
 *   <li><b>place</b>: insert into {@code edge_legal_holds} (first version wins), mark matching live
 *       rows {@code legal_hold = true}, add the hold to matching held copies, count the matches.
 *       A merchant-range hold also covers rows inserted later (insert trigger, ledger V2).</li>
 *   <li><b>release</b>: close the hold; rows no other active hold covers lose the mark. Released
 *       rows become purgeable at the next run if past the cutoff (§8.3) — the purge deletes held
 *       copies then, not this method.</li>
 * </ul>
 *
 * <p>Scope semantics: {@code edgeAssessmentIds} lists rows by id; {@code merchantId + from + to}
 * covers that merchant's rows with {@code from <= evaluatedAt <= to} — inclusive of {@code to}, the
 * conservative reading of a hold's end (§12.2; the fixture ends a range at {@code 23:59:59Z}). A
 * hold is authoritative through {@code edge_is_held()}; the
 * {@code legal_hold} column is the marker the contract asks for.
 */
public class LegalHoldService {

    private static final Logger log = LoggerFactory.getLogger(LegalHoldService.class);

    /** {@code hold.ack} states (notify/hold.ack.item.schema.json). */
    public static final String APPLIED = "APPLIED";
    public static final String RELEASED = "RELEASED";
    public static final String UNKNOWN_HOLD = "UNKNOWN_HOLD";

    /**
     * A {@code hold.place} as received.
     *
     * @param holdId     hold id (idempotency key)
     * @param placedAt   when the cloud placed it
     * @param placedBy   cloud user ref
     * @param reasonCode reason as received (an unknown code still holds, §12.2)
     * @param reference  cloud workflow reference
     * @param ids        IDS scope, or null
     * @param merchantId MERCHANT_RANGE scope merchant, or null
     * @param from       MERCHANT_RANGE start (inclusive), or null
     * @param to         MERCHANT_RANGE end (inclusive), or null
     */
    public record Placement(UUID holdId, Instant placedAt, String placedBy, String reasonCode, String reference,
                            List<UUID> ids, String merchantId, Instant from, Instant to) {

        /** Validates that exactly one scope is given. */
        public Placement {
            Objects.requireNonNull(holdId, "holdId");
            boolean byIds = ids != null && !ids.isEmpty();
            boolean byRange = merchantId != null && from != null && to != null;
            if (byIds == byRange) {
                throw new IllegalArgumentException("a hold has exactly one scope");
            }
            ids = byIds ? List.copyOf(ids) : null;
        }

        /** Whether the scope is a list of ids. */
        public boolean byIds() {
            return ids != null;
        }
    }

    /**
     * The {@code hold.ack} item.
     *
     * @param holdId           the hold
     * @param state            {@link #APPLIED}, {@link #RELEASED} or {@link #UNKNOWN_HOLD}
     * @param matchedRows      rows (live and held) the hold's scope covers
     * @param activeHoldDigest SHA-256 hex of the sorted, newline-joined active hold ids
     */
    public record Ack(UUID holdId, String state, long matchedRows, String activeHoldDigest) {
    }

    /**
     * Hold figures for {@code ledger.health} (§7.6).
     *
     * @param active           active holds
     * @param heldRows         marked live rows plus held copies
     * @param activeHoldDigest digest of the active hold ids
     */
    public record Summary(long active, long heldRows, String activeHoldDigest) {
    }

    private final EdgeLedger ledger;

    /** @param ledger the site ledger */
    public LegalHoldService(EdgeLedger ledger) {
        this.ledger = ledger;
    }

    /**
     * Places a hold (idempotent): the first {@code hold.place} for an id defines it; a re-send
     * re-marks the rows and answers the current state.
     *
     * @throws SQLException when the ledger cannot be written (the cloud re-sends the notify)
     */
    public Ack place(Placement p) throws SQLException {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                lockHold(c, p.holdId());
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO edge_legal_holds (hold_id, placed_at, placed_by, reason_code, reference, scope_kind,
                                                      edge_assessment_ids, merchant_id, range_from, range_to)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (hold_id) DO NOTHING
                        """)) {
                    ps.setObject(1, p.holdId());
                    ps.setObject(2, utc(p.placedAt()));
                    ps.setString(3, p.placedBy());
                    ps.setString(4, p.reasonCode());
                    ps.setString(5, p.reference());
                    ps.setString(6, p.byIds() ? "IDS" : "MERCHANT_RANGE");
                    ps.setArray(7, p.byIds() ? c.createArrayOf("uuid", p.ids().toArray()) : null);
                    ps.setString(8, p.byIds() ? null : p.merchantId());
                    ps.setObject(9, p.byIds() ? null : utc(p.from()));
                    ps.setObject(10, p.byIds() ? null : utc(p.to()));
                    ps.executeUpdate();
                }
                StoredHold stored = load(c, p.holdId());
                if (stored == null) {
                    throw new SQLException("hold row vanished inside its own transaction", "XX000");
                }
                String state = stored.released ? RELEASED : APPLIED;
                if (!stored.released) {
                    mark(c, stored);
                }
                long matched = countMatches(c, stored);
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE edge_legal_holds SET matched_rows = ? WHERE hold_id = ?")) {
                    ps.setLong(1, matched);
                    ps.setObject(2, p.holdId());
                    ps.executeUpdate();
                }
                String digest = activeHoldDigest(c);
                c.commit();
                log.info("Legal hold {} {} ({} row(s) covered)", p.holdId(), state.toLowerCase(), matched);
                return new Ack(p.holdId(), state, matched, digest);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Releases a hold (idempotent). An unknown hold answers {@link #UNKNOWN_HOLD}.
     *
     * @throws SQLException when the ledger cannot be written
     */
    public Ack release(UUID holdId, Instant releasedAt, String releasedBy, String reasonCode) throws SQLException {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                lockHold(c, holdId);
                StoredHold stored = load(c, holdId);
                if (stored == null) {
                    String digest = activeHoldDigest(c);
                    c.commit();
                    return new Ack(holdId, UNKNOWN_HOLD, 0, digest);
                }
                if (!stored.released) {
                    try (PreparedStatement ps = c.prepareStatement("""
                            UPDATE edge_legal_holds SET released_at = ?, released_by = ?, release_reason_code = ?
                             WHERE hold_id = ? AND released_at IS NULL
                            """)) {
                        ps.setObject(1, utc(releasedAt));
                        ps.setString(2, releasedBy);
                        ps.setString(3, reasonCode);
                        ps.setObject(4, holdId);
                        ps.executeUpdate();
                    }
                    unmark(c, stored);
                }
                long matched = countMatches(c, stored);
                String digest = activeHoldDigest(c);
                c.commit();
                log.info("Legal hold {} released ({} row(s) were covered)", holdId, matched);
                return new Ack(holdId, RELEASED, matched, digest);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** SHA-256 hex of the sorted, newline-joined active hold ids (§7.10). */
    public String activeHoldDigest() throws SQLException {
        try (Connection c = connection()) {
            return activeHoldDigest(c);
        }
    }

    /** Active holds, held rows and the digest, for {@code ledger.health}. */
    public Summary summary() throws SQLException {
        try (Connection c = connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT (SELECT count(*) FROM edge_legal_holds WHERE released_at IS NULL),
                            (SELECT count(*) FROM edge_evaluations WHERE legal_hold)
                          + (SELECT count(*) FROM edge_held_evaluations)
                     """)) {
            rs.next();
            return new Summary(rs.getLong(1), rs.getLong(2), activeHoldDigest(c));
        }
    }

    /** SHA-256 over the active ids, sorted as lowercase strings, joined with {@code \n}. */
    static String activeHoldDigest(Connection c) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT hold_id::text FROM edge_legal_holds WHERE released_at IS NULL ORDER BY hold_id::text")) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        }
        return Jcs.sha256Hex(String.join("\n", ids).getBytes(StandardCharsets.UTF_8));
    }

    /** One stored hold's scope and state. */
    private record StoredHold(UUID holdId, boolean released, UUID[] ids, String merchantId,
                              OffsetDateTime from, OffsetDateTime to) {
    }

    /** Reads a hold row inside the caller's transaction; null when absent. */
    private static StoredHold load(Connection c, UUID holdId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT released_at IS NOT NULL, edge_assessment_ids, merchant_id, range_from, range_to
                  FROM edge_legal_holds WHERE hold_id = ?
                """)) {
            ps.setObject(1, holdId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                java.sql.Array arr = rs.getArray(2);
                UUID[] ids = arr == null ? null : (UUID[]) arr.getArray();
                return new StoredHold(holdId, rs.getBoolean(1), ids, rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class), rs.getObject(5, OffsetDateTime.class));
            }
        }
    }

    /** {@code WHERE} fragment of a hold's scope over a table aliased {@code e}. */
    private static String scope(StoredHold h) {
        return h.ids() != null
                ? "e.edge_assessment_id = ANY (?)"
                : "e.merchant_id = ? AND e.evaluated_at >= ? AND e.evaluated_at <= ?";
    }

    /** Binds the parameters of {@link #scope} starting at {@code index}; returns the next index. */
    private static int bindScope(Connection c, PreparedStatement ps, int index, StoredHold h) throws SQLException {
        if (h.ids() != null) {
            ps.setArray(index++, c.createArrayOf("uuid", h.ids()));
        } else {
            ps.setString(index++, h.merchantId());
            ps.setObject(index++, h.from());
            ps.setObject(index++, h.to());
        }
        return index;
    }

    /** Marks live rows and records the hold on held copies. */
    private static void mark(Connection c, StoredHold h) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE edge_evaluations e SET legal_hold = true WHERE NOT e.legal_hold AND " + scope(h))) {
            bindScope(c, ps, 1, h);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE edge_held_evaluations e
                   SET hold_ids = (SELECT array_agg(DISTINCT x ORDER BY x) FROM unnest(array_append(e.hold_ids, ?)) x),
                       legal_hold = true
                 WHERE NOT (? = ANY (e.hold_ids)) AND
                """ + scope(h))) {
            ps.setObject(1, h.holdId());
            ps.setObject(2, h.holdId());
            bindScope(c, ps, 3, h);
            ps.executeUpdate();
        }
    }

    /** Clears the mark on rows no other active hold covers and drops the hold from held copies. */
    private static void unmark(Connection c, StoredHold h) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE edge_evaluations e SET legal_hold = false
                 WHERE e.legal_hold AND NOT edge_is_held(e.edge_assessment_id, e.merchant_id, e.evaluated_at) AND
                """ + scope(h))) {
            bindScope(c, ps, 1, h);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE edge_held_evaluations e
                   SET hold_ids = array_remove(e.hold_ids, ?),
                       legal_hold = cardinality(array_remove(e.hold_ids, ?)) > 0
                 WHERE ? = ANY (e.hold_ids)
                """)) {
            ps.setObject(1, h.holdId());
            ps.setObject(2, h.holdId());
            ps.setObject(3, h.holdId());
            ps.executeUpdate();
        }
    }

    /** Rows (live and held) inside the hold's scope. */
    private static long countMatches(Connection c, StoredHold h) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM edge_all_evaluations e WHERE " + scope(h))) {
            bindScope(c, ps, 1, h);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Serialises concurrent place/release of one hold across the nodes of a site. */
    private static void lockHold(Connection c, UUID holdId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
            ps.setString(1, "hokeka.edge.hold:" + holdId);
            ps.executeQuery().close();
        }
    }

    /** A connection from the write pool, or a fail-fast error when the ledger is not ready. */
    private Connection connection() throws SQLException {
        if (!ledger.ready() || ledger.dataSource() == null) {
            throw new SQLException("Edge Ledger is not ready", "08000");
        }
        return ledger.dataSource().getConnection();
    }

    /** An instant as a UTC {@link OffsetDateTime} for JDBC. */
    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}
