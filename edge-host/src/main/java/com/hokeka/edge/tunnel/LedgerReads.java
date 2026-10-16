package com.hokeka.edge.tunnel;

import com.hokeka.edge.ledger.EdgeLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read queries of the tunnel ops over the Edge Ledger (EDGE_DATA_RESIDENCY_CONTRACT.md §7.6),
 * against {@code edge_all_evaluations} (live and held rows) on the separate read pool.
 *
 * <p>Every statement runs in a read-only transaction with {@code statement_timeout} taken from the
 * request's remaining budget, and is registered with the request's {@link RequestControl} so a
 * cancel stops it. {@code pan_hash} is never selected into a {@link Row}: it is used only inside
 * SQL (linkage, CTR) and never reaches an item. Ordering is always (evaluated_at DESC,
 * edge_assessment_id DESC); PostgreSQL orders {@code uuid} bytewise, which is the order of the
 * canonical lowercase strings the contract specifies.
 */
public final class LedgerReads {

    private static final Logger log = LoggerFactory.getLogger(LedgerReads.class);

    /** PostgreSQL SQLSTATE query_canceled (statement_timeout and pg_cancel_backend). */
    static final String QUERY_CANCELED = "57014";

    /** Summary columns (no features, reasons or sanctions details). */
    static final String SUMMARY_COLUMNS = "e.edge_assessment_id, e.txn_ref, e.site_id, e.node_id, e.evaluated_at, "
            + "e.origin, e.amount_cents, e.currency, e.merchant_id, e.mcc, e.country_code, e.channel, e.action, e.score, "
            + "e.triggered_rule_ids, e.ctr_required, e.sanctions_hit";
    /** Detail columns: summary plus the detail-only members and the authoritative hold flag. */
    static final String DETAIL_COLUMNS = SUMMARY_COLUMNS + ", e.rule_version_ids, e.reasons, e.bundle_version, "
            + "e.bundle_hash, e.evaluator, e.latency_us, e.fail_closed_code, e.sanctions_hits::text, e.typology_codes, "
            + "e.features::text, edge_is_held(e.edge_assessment_id, e.merchant_id, e.evaluated_at)";

    private static final String ORDER = " ORDER BY e.evaluated_at DESC, e.edge_assessment_id DESC";

    /**
     * One evaluation as read for an item. Detail-only members are null in a summary row.
     */
    public record Row(UUID id, String txnRef, String siteId, String nodeId, Instant evaluatedAt, String origin,
                      Long amountMinor, String currency, String merchantId, String mcc, String countryCode,
                      String channel, String decision, int score, List<Long> triggeredRuleIds, boolean ctrRequired,
                      boolean sanctionsHit, List<Long> ruleVersionIds, List<String> reasons, Long bundleVersion,
                      String bundleHash, String evaluator, Long latencyUs, String failClosedCode,
                      String sanctionsHitsJson, List<String> typologyCodes, String featuresJson, Boolean legalHold) {

        /** Whether this row carries the detail members. */
        public boolean detail() {
            return featuresJson != null;
        }
    }

    /**
     * The {@code searchFilter} of txn.search / export.stream (§7.6).
     *
     * @param from         inclusive start
     * @param to           exclusive end
     * @param merchantId   exact merchant, or null
     * @param decisions    decisions to include, or empty for all
     * @param ruleId       triggered rule, or null
     * @param txnRef       exact txn ref, or null
     * @param minScore     score floor, or null
     * @param ctrRequired  CTR flag, or null
     * @param sanctionsHit sanctions flag, or null
     */
    public record Filter(Instant from, Instant to, String merchantId, List<String> decisions, Long ruleId,
                         String txnRef, Integer minScore, Boolean ctrRequired, Boolean sanctionsHit) {
    }

    /** Linkage keys of an anchor row (never returned; used only in SQL). */
    public record AnchorKeys(UUID id, Instant evaluatedAt, String panHash, String merchantId, String deviceId,
                             String accountId, String customerId, String ipAddress) {
    }

    /** A unit of read work inside one budgeted transaction. */
    @FunctionalInterface
    public interface Work<T> {
        /** Runs the work; statements must be prepared through {@link Session}. */
        T run(Session session) throws SQLException, TunnelException;
    }

    /** A budgeted read transaction: prepares statements under the request's control. */
    public static final class Session {
        private final Connection connection;
        private final RequestControl control;

        Session(Connection connection, RequestControl control) {
            this.connection = connection;
            this.control = control;
        }

        /** The underlying connection (for arrays). */
        public Connection connection() {
            return connection;
        }

        /** Prepares a statement and re-arms the statement timeout from the remaining budget. */
        public PreparedStatement prepare(String sql) throws SQLException, TunnelException {
            long ms = control.statementTimeoutMs();
            try (Statement st = connection.createStatement()) {
                st.execute("SET LOCAL statement_timeout = " + ms);
            }
            PreparedStatement ps = connection.prepareStatement(sql);
            control.register(ps);
            return ps;
        }

        /** Throws when the request was cancelled or ran out of budget. */
        public void checkpoint() throws TunnelException {
            control.checkpoint();
        }
    }

    private final EdgeLedger ledger;

    /** @param ledger the site ledger (its read pool is used) */
    public LedgerReads(EdgeLedger ledger) {
        this.ledger = ledger;
    }

    /**
     * Runs {@code work} in one read-only transaction under {@code control}, mapping database
     * failures onto §7.11 codes: statement timeout → {@code DEADLINE_EXCEEDED}, cancel →
     * {@code CANCELLED}, unreachable ledger → {@code LEDGER_UNAVAILABLE}, anything else →
     * {@code INTERNAL} (logged by SQLSTATE only).
     */
    public <T> T read(RequestControl control, Work<T> work) throws TunnelException {
        if (!ledger.ready()) {
            throw new TunnelException(TunnelErrorCode.LEDGER_UNAVAILABLE, "Edge Ledger is not available");
        }
        DataSource ds = ledger.readDataSource();
        if (ds == null) {
            throw new TunnelException(TunnelErrorCode.LEDGER_UNAVAILABLE, "Edge Ledger is not configured");
        }
        control.checkpoint();
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(new Session(c, control));
                c.commit();
                return result;
            } catch (SQLException | TunnelException | RuntimeException e) {
                rollbackQuietly(c);
                throw e;
            } finally {
                control.unregister();
            }
        } catch (SQLException e) {
            throw map(e, control);
        }
    }

    /** Maps a JDBC failure onto a tunnel error (no values in the message). */
    static TunnelException map(SQLException e, RequestControl control) {
        String state = e.getSQLState();
        if (QUERY_CANCELED.equals(state)) {
            return control.cancelled()
                    ? new TunnelException(control.cancelCode(), "request was cancelled")
                    : new TunnelException(TunnelErrorCode.DEADLINE_EXCEEDED, "ledger statement exceeded the request budget");
        }
        if (e instanceof SQLTransientConnectionException || (state != null && state.startsWith("08"))) {
            return new TunnelException(TunnelErrorCode.LEDGER_UNAVAILABLE, "Edge Ledger is not reachable");
        }
        log.warn("Tunnel ledger read failed ({})", EdgeLedger.safeMessage(e));
        return new TunnelException(TunnelErrorCode.INTERNAL, "ledger read failed");
    }

    /** Rolls back without masking the original failure. */
    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
            // The connection is broken; the original failure is reported.
        }
    }

    // ── queries ──────────────────────────────────────────────────────────────────────────────────

    /**
     * One keyset page of rows matching {@code filter}, strictly after {@code after}.
     *
     * @param detail whether to read detail columns
     * @param limit  rows to return (callers ask for one more than they need to detect a next page)
     */
    public static List<Row> search(Session s, Filter filter, TunnelCursor.Key after, int limit, boolean detail)
            throws SQLException, TunnelException {
        StringBuilder sql = new StringBuilder("SELECT ").append(detail ? DETAIL_COLUMNS : SUMMARY_COLUMNS)
                .append(" FROM edge_all_evaluations e WHERE e.evaluated_at >= ? AND e.evaluated_at < ?");
        List<Object> args = new ArrayList<>();
        args.add(utc(filter.from()));
        args.add(utc(filter.to()));
        if (filter.merchantId() != null) {
            sql.append(" AND e.merchant_id = ?");
            args.add(filter.merchantId());
        }
        if (filter.decisions() != null && !filter.decisions().isEmpty()) {
            sql.append(" AND e.action = ANY (?)");
            args.add(new TextArray(filter.decisions()));
        }
        if (filter.ruleId() != null) {
            sql.append(" AND ? = ANY (e.triggered_rule_ids)");
            args.add(filter.ruleId());
        }
        if (filter.txnRef() != null) {
            sql.append(" AND e.txn_ref = ?");
            args.add(filter.txnRef());
        }
        if (filter.minScore() != null) {
            sql.append(" AND e.score >= ?");
            args.add(filter.minScore());
        }
        if (filter.ctrRequired() != null) {
            sql.append(" AND e.ctr_required = ?");
            args.add(filter.ctrRequired());
        }
        if (filter.sanctionsHit() != null) {
            sql.append(" AND e.sanctions_hit = ?");
            args.add(filter.sanctionsHit());
        }
        if (after != null) {
            sql.append(" AND (e.evaluated_at, e.edge_assessment_id) < (?, ?)");
            args.add(utc(after.evaluatedAt()));
            args.add(after.id());
        }
        sql.append(ORDER).append(" LIMIT ?");
        args.add(limit);
        try (PreparedStatement ps = s.prepare(sql.toString())) {
            bind(s.connection(), ps, args);
            return rows(ps, detail);
        }
    }

    /** Detail rows by id, keyed by id (absent ids are simply missing from the map). */
    public static Map<UUID, Row> byIds(Session s, List<UUID> ids) throws SQLException, TunnelException {
        Map<UUID, Row> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        try (PreparedStatement ps = s.prepare("SELECT " + DETAIL_COLUMNS
                + " FROM edge_all_evaluations e WHERE e.edge_assessment_id = ANY (?)")) {
            ps.setArray(1, s.connection().createArrayOf("uuid", ids.toArray()));
            for (Row r : rows(ps, true)) {
                out.putIfAbsent(r.id(), r);
            }
        }
        return out;
    }

    /** The detail row a txn ref points at (the site's unique index, §7.6 txn.get). */
    public static Optional<Row> byTxnRef(Session s, String txnRef) throws SQLException, TunnelException {
        UUID id;
        try (PreparedStatement ps = s.prepare("SELECT edge_assessment_id FROM edge_txn_refs WHERE txn_ref = ?")) {
            ps.setString(1, txnRef);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                id = rs.getObject(1, UUID.class);
            }
        }
        return Optional.ofNullable(byIds(s, List.of(id)).get(id));
    }

    /** The linkage keys of one row (CARD / MERCHANT / DEVICE / ACCOUNT / IP, §7.6). */
    public static Optional<AnchorKeys> anchorKeys(Session s, UUID id) throws SQLException, TunnelException {
        try (PreparedStatement ps = s.prepare("""
                SELECT e.edge_assessment_id, e.evaluated_at, e.pan_hash, e.merchant_id, e.features ->> 'device_id',
                       e.features ->> 'account_id', e.features ->> 'customer_id', e.features ->> 'ip_address'
                  FROM edge_all_evaluations e WHERE e.edge_assessment_id = ? LIMIT 1
                """)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AnchorKeys(rs.getObject(1, UUID.class), instant(rs, 2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)));
            }
        }
    }

    /**
     * Rows linked to any of the given key sets, inside {@code [from, to]}, excluding
     * {@code excluded}, after {@code after}, ordered, at most {@code limit}. Each row also carries
     * its own keys so the caller can name the link and expand a second hop.
     *
     * @param links key kind → values to match: {@code CARD} (pan_hash), {@code MERCHANT},
     *              {@code DEVICE} (features.device_id), {@code ACCOUNT_ID} (features.account_id),
     *              {@code CUSTOMER_ID} (features.customer_id), {@code IP} (features.ip_address)
     */
    public static List<LinkedRow> linked(Session s, Map<String, List<String>> links, Instant from, Instant to,
                                         List<UUID> excluded, TunnelCursor.Key after, int limit)
            throws SQLException, TunnelException {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        args.add(utc(from));
        args.add(utc(to));
        for (Map.Entry<String, List<String>> link : links.entrySet()) {
            if (link.getValue().isEmpty()) {
                continue;
            }
            TextArray values = new TextArray(link.getValue());
            switch (link.getKey()) {
                case "CARD" -> {
                    conditions.add("e.pan_hash = ANY (?)");
                    args.add(values);
                }
                case "MERCHANT" -> {
                    conditions.add("e.merchant_id = ANY (?)");
                    args.add(values);
                }
                case "DEVICE" -> {
                    conditions.add("(e.features ->> 'device_id') = ANY (?)");
                    args.add(values);
                }
                case "ACCOUNT_ID" -> {
                    conditions.add("(e.features ->> 'account_id') = ANY (?)");
                    args.add(values);
                }
                case "CUSTOMER_ID" -> {
                    conditions.add("(e.features ->> 'customer_id') = ANY (?)");
                    args.add(values);
                }
                case "IP" -> {
                    conditions.add("(e.features ->> 'ip_address') = ANY (?)");
                    args.add(values);
                }
                default -> throw new IllegalArgumentException("unknown link type " + link.getKey());
            }
        }
        if (conditions.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(SUMMARY_COLUMNS)
                .append(", e.pan_hash, e.features ->> 'device_id', e.features ->> 'account_id', "
                        + "e.features ->> 'customer_id', e.features ->> 'ip_address'"
                        + " FROM edge_all_evaluations e WHERE e.evaluated_at >= ? AND e.evaluated_at <= ? AND (")
                .append(String.join(" OR ", conditions)).append(")");
        if (!excluded.isEmpty()) {
            sql.append(" AND NOT (e.edge_assessment_id = ANY (?))");
            args.add(new UuidArray(excluded));
        }
        if (after != null) {
            sql.append(" AND (e.evaluated_at, e.edge_assessment_id) < (?, ?)");
            args.add(utc(after.evaluatedAt()));
            args.add(after.id());
        }
        sql.append(ORDER).append(" LIMIT ?");
        args.add(limit);
        List<LinkedRow> out = new ArrayList<>();
        try (PreparedStatement ps = s.prepare(sql.toString())) {
            bind(s.connection(), ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Row row = row(rs, false);
                    int k = 18;
                    out.add(new LinkedRow(row, new AnchorKeys(row.id(), row.evaluatedAt(), rs.getString(k),
                            row.merchantId(), rs.getString(k + 1), rs.getString(k + 2), rs.getString(k + 3),
                            rs.getString(k + 4))));
                }
            }
        }
        return out;
    }

    /** A linked row with its own linkage keys. */
    public record LinkedRow(Row row, AnchorKeys keys) {
    }

    /** One cash transaction of a card's business day (CTR aggregation, §7.6 filing.extract). */
    public record CashTxn(UUID id, long amountMinor, String currency) {
    }

    /**
     * The card's cash transactions with {@code from <= evaluated_at < to} on the given channels
     * (amount and currency present), oldest first. The card is named by the anchor's id; its
     * {@code pan_hash} stays inside SQL.
     */
    public static List<CashTxn> cashDay(Session s, UUID anchorId, Instant from, Instant to, List<String> cashChannels,
                                        int limit) throws SQLException, TunnelException {
        if (cashChannels.isEmpty()) {
            return List.of();
        }
        List<CashTxn> out = new ArrayList<>();
        try (PreparedStatement ps = s.prepare("""
                SELECT e.edge_assessment_id, e.amount_cents, e.currency
                  FROM edge_all_evaluations e
                 WHERE e.pan_hash = (SELECT a.pan_hash FROM edge_all_evaluations a WHERE a.edge_assessment_id = ? LIMIT 1)
                   AND e.evaluated_at >= ? AND e.evaluated_at < ?
                   AND e.channel = ANY (?)
                   AND e.amount_cents IS NOT NULL AND e.currency IS NOT NULL
                 ORDER BY e.evaluated_at, e.edge_assessment_id
                 LIMIT ?
                """)) {
            ps.setObject(1, anchorId);
            ps.setObject(2, utc(from));
            ps.setObject(3, utc(to));
            ps.setArray(4, s.connection().createArrayOf("text", cashChannels.toArray()));
            ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CashTxn(rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3)));
                }
            }
        }
        return out;
    }

    // ── mapping ──────────────────────────────────────────────────────────────────────────────────

    /** All rows of a statement. */
    private static List<Row> rows(PreparedStatement ps, boolean detail) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(row(rs, detail));
            }
        }
        return out;
    }

    /** One row from the current result position (columns in SUMMARY/DETAIL order). */
    static Row row(ResultSet rs, boolean detail) throws SQLException {
        UUID id = rs.getObject(1, UUID.class);
        long amount = rs.getLong(7);
        Long amountMinor = rs.wasNull() ? null : amount;
        List<Long> triggered = longs(rs.getArray(15));
        if (!detail) {
            return new Row(id, rs.getString(2), rs.getString(3), rs.getString(4), instant(rs, 5), rs.getString(6),
                    amountMinor, rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                    rs.getString(13), rs.getInt(14), triggered, rs.getBoolean(16), rs.getBoolean(17), null, null, null,
                    null, null, null, null, null, null, null, null);
        }
        return new Row(id, rs.getString(2), rs.getString(3), rs.getString(4), instant(rs, 5), rs.getString(6),
                amountMinor, rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                rs.getString(13), rs.getInt(14), triggered, rs.getBoolean(16), rs.getBoolean(17), longs(rs.getArray(18)),
                strings(rs.getArray(19)), rs.getLong(20), rs.getString(21), rs.getString(22), rs.getLong(23),
                rs.getString(24), rs.getString(25), strings(rs.getArray(26)), rs.getString(27), rs.getBoolean(28));
    }

    /** A timestamptz column as an instant. */
    static Instant instant(ResultSet rs, int col) throws SQLException {
        OffsetDateTime t = rs.getObject(col, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    /** A bigint[] column as a list. */
    private static List<Long> longs(Array a) throws SQLException {
        if (a == null) {
            return List.of();
        }
        Object[] values = (Object[]) a.getArray();
        List<Long> out = new ArrayList<>(values.length);
        for (Object v : values) {
            out.add(((Number) v).longValue());
        }
        return out;
    }

    /** A text[] column as a list. */
    private static List<String> strings(Array a) throws SQLException {
        if (a == null) {
            return List.of();
        }
        return Arrays.asList((String[]) a.getArray());
    }

    /** An instant as a UTC OffsetDateTime for JDBC. */
    static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /** Marker for a text[] parameter. */
    private record TextArray(List<String> values) {
    }

    /** Marker for a uuid[] parameter. */
    private record UuidArray(List<UUID> values) {
    }

    /** Binds positional parameters, turning array markers into SQL arrays. */
    private static void bind(Connection c, PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            Object a = args.get(i);
            if (a instanceof TextArray t) {
                ps.setArray(i + 1, c.createArrayOf("text", t.values().toArray()));
            } else if (a instanceof UuidArray u) {
                ps.setArray(i + 1, c.createArrayOf("uuid", u.values().toArray()));
            } else {
                ps.setObject(i + 1, a);
            }
        }
    }
}
