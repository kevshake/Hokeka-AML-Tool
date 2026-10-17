package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads legacy cloud rows for one back-fill run, straight from the database (no JPA, so no entity
 * cache, Envers or Hibernate filter holds them). Every query is scoped to the run's PSP: the run row
 * is joined and {@code psp_id = run.psp_id} is a predicate of every table read (tenant isolation).
 *
 * <p><b>Selection (contract §11 step 2).</b> Rows with evaluation time in
 * [{@code retention_cutoff}, {@code source_to}), ordered by (evaluation time, id), that route to the
 * run's site and are not already covered by a {@code VERIFIED} run of the PSP. Routing (R15 site
 * rule): the site of the row's cloud decision record when it is a current site of the PSP; else the
 * merchant's frozen site ({@code ledger_backfill_merchant_sites}); else the PSP's default target
 * site. Multi-asset rows have neither and always go to the default site.
 */
@Component
public class LegacyRowReader {

    /** Expression of a card row's evaluation time (ingest without txn_ts falls back to created_at). */
    private static final String TXN_TS = "COALESCE(t.txn_ts, t.created_at)";

    /** The run's resolved site of a card row (R15 routing rule). */
    private static final String TXN_SITE = """
            CASE WHEN a.site_id IS NOT NULL AND r.site_ids_snapshot @> jsonb_build_array(a.site_id) THEN a.site_id
                 WHEN ms.site_id IS NOT NULL AND r.site_ids_snapshot @> jsonb_build_array(ms.site_id) THEN ms.site_id
                 ELSE (SELECT g.default_site_id FROM ledger_backfill_targets g WHERE g.psp_id = r.psp_id) END""";

    /** A legacy row of {@code source} already held or resolved by a VERIFIED run of the PSP. */
    private static final String COVERED = """
            EXISTS (SELECT 1 FROM ledger_backfill_rows c JOIN ledger_backfill_runs v ON v.backfill_id = c.backfill_id
                     WHERE v.psp_id = r.psp_id AND v.state = 'VERIFIED' AND c.psp_id = r.psp_id
                       AND c.source = '%s' AND c.source_id = %s
                       AND (c.outcome = 'HELD' OR c.resolution IS NOT NULL))""";

    private static final String TXN_SELECT = """
            SELECT r.psp_id AS psp_id_of_run, t.txn_id, %s AS eff_ts, t.pan_hash, t.merchant_id, t.client_reference, t.terminal_id,
                   t.amount_cents, t.currency, t.ip_address, t.device_fingerprint, t.krs, t.trs, t.cra, t.direction,
                   t.merchant_country, t.risk_level, t.decision, t.decision_v2, t.sar_required, t.ctr_required,
                   t.cash_transaction, t.ctr_evaluation_status, t.ctr_usd_equivalent, t.ctr_threshold_usd,
                   t.rule_decision, t.triggered_rules, t.card_brand, t.card_type, t.card_class, t.channel_type,
                   t.bill_classification_code, t.customer_account_reference,
                   f.feature_json, f.score AS f_score, f.action_taken AS f_action, f.model_version AS f_model,
                   a.id AS a_id, a.edge_assessment_id AS a_edge_id, a.txn_ref AS a_txn_ref, a.score AS a_score,
                   a.triggered_rule_ids AS a_rules, a.rule_version_ids AS a_versions, a.decision AS a_decision
              FROM transactions t
              JOIN ledger_backfill_runs r ON r.backfill_id = ?
              LEFT JOIN transaction_features f ON f.txn_id = t.txn_id
              LEFT JOIN LATERAL (
                    SELECT x.id, x.edge_assessment_id, x.txn_ref, x.site_id, x.score, x.triggered_rule_ids,
                           x.rule_version_ids, x.decision
                      FROM assessments x
                     WHERE x.txn_id = t.txn_id AND x.psp_id = t.psp_id AND x.trigger_type = 'TXN'
                     ORDER BY (x.edge_assessment_id IS NULL), x.created_at, x.id
                     LIMIT 1) a ON TRUE
              LEFT JOIN ledger_backfill_merchant_sites ms ON ms.psp_id = t.psp_id AND ms.merchant_id = t.merchant_id
             WHERE t.psp_id = r.psp_id
            """.formatted(TXN_TS);

    private static final String TXN_WINDOW = """
               AND %1$s >= ?
               AND %1$s < ?
               AND (%2$s) = r.site_id
               AND NOT %3$s
            """.formatted(TXN_TS, TXN_SITE, COVERED.formatted("TXN", "t.txn_id"));

    private static final String MAT_SELECT = """
            SELECT r.psp_id AS psp_id_of_run, m.id, m.executed_at AS eff_ts, m.external_transaction_id, m.customer_id, m.asset_class,
                   m.product_domain, m.source_product_domain, m.destination_product_domain, m.transaction_type,
                   m.amount, m.fiat_equivalent_usd, m.currency, m.asset_symbol, m.quantity, m.unit_price,
                   m.country_code, m.device_fingerprint, m.ip_address, m.counterparty_reference,
                   m.funding_party_customer_id, m.source_network, m.destination_network, m.originator_name,
                   m.originator_account, m.beneficiary_name, m.beneficiary_account, m.merchant_category,
                   m.travel_rule_required, m.travel_rule_status, m.risk_score, m.decision, m.metadata,
                   mm.event_type AS mm_event_type, mm.wallet_reference AS mm_wallet,
                   mm.counterparty_wallet_reference AS mm_counterparty_wallet, mm.agent_reference AS mm_agent,
                   mm.merchant_reference AS mm_merchant, mm.cell_id AS mm_cell, mm.cross_border AS mm_cross_border,
                   mm.device_fingerprint AS mm_device, mm.agent_device_fingerprint AS mm_agent_device,
                   mm.sim_identifier_hash AS mm_sim, mm.latitude AS mm_lat, mm.longitude AS mm_lon,
                   mm.agent_latitude AS mm_agent_lat, mm.agent_longitude AS mm_agent_lon,
                   mm.sim_changed_at AS mm_sim_changed, mm.device_registered_at AS mm_device_registered,
                   mm.wallet_previous_activity_at AS mm_wallet_previous, mm.agent_float_before AS mm_float_before,
                   mm.agent_float_after AS mm_float_after,
                   tr.status AS tr_status, tr.jurisdiction AS tr_jurisdiction, tr.protocol AS tr_protocol,
                   tr.originator_verification AS tr_orig_verif, tr.beneficiary_verification AS tr_bene_verif,
                   (SELECT string_agg(DISTINCT s.signal_code, ',' ORDER BY s.signal_code)
                      FROM multi_asset_risk_signals s WHERE s.transaction_id = m.id AND s.psp_id = m.psp_id) AS signals
              FROM multi_asset_transactions m
              JOIN ledger_backfill_runs r ON r.backfill_id = ?
              LEFT JOIN mobile_money_transaction_contexts mm ON mm.transaction_id = m.id AND mm.psp_id = m.psp_id
              LEFT JOIN travel_rule_transfers tr ON tr.transaction_id = m.id AND tr.psp_id = m.psp_id
             WHERE m.psp_id = r.psp_id
            """;

    private static final String MAT_WINDOW = """
               AND m.executed_at >= ?
               AND m.executed_at < ?
               AND r.default_site
               AND NOT %s
            """.formatted(COVERED.formatted("MAT", "m.id"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ZoneId zone;

    /** Reads through the application's primary database, legacy timestamps in the configured zone. */
    public LegacyRowReader(JdbcTemplate jdbc, ObjectMapper mapper, BackfillProperties props) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.zone = props.legacyZone();
    }

    /** A keyset position: (evaluation time, id) of the last row consumed. */
    public record Position(LocalDateTime ts, long id) {}

    /** Number of rows the run selects in [{@code from}, {@code to}), across both sources. */
    public long count(UUID backfillId, Instant from, Instant to) {
        Long txn = jdbc.queryForObject("SELECT count(*) FROM (" + TXN_SELECT + TXN_WINDOW + ") q", Long.class,
                backfillId, local(from), local(to));
        Long mat = jdbc.queryForObject("SELECT count(*) FROM (" + MAT_SELECT + MAT_WINDOW + ") q", Long.class,
                backfillId, local(from), local(to));
        return (txn == null ? 0 : txn) + (mat == null ? 0 : mat);
    }

    /**
     * Up to {@code limit} rows of {@code source} with evaluation time in [{@code from}, {@code to}),
     * after {@code after} (null = from the start), in send order.
     */
    public List<LegacyRow> next(UUID backfillId, Instant from, Instant to, LegacySource source, Position after,
                                int limit) {
        boolean txn = source == LegacySource.TXN;
        StringBuilder sql = new StringBuilder(txn ? TXN_SELECT + TXN_WINDOW : MAT_SELECT + MAT_WINDOW);
        List<Object> args = new ArrayList<>();
        args.add(backfillId);
        args.add(local(from));
        args.add(local(to));
        String ts = txn ? TXN_TS : "m.executed_at";
        String id = txn ? "t.txn_id" : "m.id";
        if (after != null) {
            sql.append(" AND (").append(ts).append(", ").append(id).append(") > (?, ?)");
            args.add(after.ts());
            args.add(after.id());
        }
        sql.append(" ORDER BY ").append(ts).append(", ").append(id).append(" LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), txn ? this::txnRow : this::matRow, args.toArray());
    }

    /**
     * The given rows of the run's PSP regardless of window and coverage (used to re-derive what was
     * sent when verifying a sample). Ids of another PSP return nothing.
     */
    public List<LegacyRow> byIds(UUID backfillId, LegacySource source, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        boolean txn = source == LegacySource.TXN;
        StringBuilder sql = new StringBuilder(txn ? TXN_SELECT : MAT_SELECT);
        sql.append(txn ? " AND t.txn_id IN (" : " AND m.id IN (");
        List<Object> args = new ArrayList<>();
        args.add(backfillId);
        Iterator<Long> it = ids.iterator();
        while (it.hasNext()) {
            sql.append('?');
            args.add(it.next());
            if (it.hasNext()) {
                sql.append(',');
            }
        }
        sql.append(')');
        RowMapper<LegacyRow> rm = txn ? this::txnRow : this::matRow;
        return jdbc.query(sql.toString(), rm, args.toArray());
    }

    /** Keyset position of a row: its evaluation time exactly as the database holds it, and its id. */
    public static Position positionOf(LegacyRow row) {
        return new Position(row.sortTs(), row.sourceId());
    }

    // ── row mapping ──────────────────────────────────────────────────────────────────────────────

    /** A card / cloud-ingest row with its features row and assessment. */
    private LegacyRow txnRow(ResultSet rs, int i) throws SQLException {
        Map<String, Object> f = new LinkedHashMap<>();
        Object stored = parseObject(rs.getString("feature_json"), f, "legacy_features_unreadable");
        if (stored instanceof Map<?, ?> m) {
            m.forEach((k, v) -> f.put(String.valueOf(k), v));
        }
        put(f, "terminal_id", rs.getString("terminal_id"));
        put(f, "ip_address", rs.getString("ip_address"));
        put(f, "device_id", rs.getString("device_fingerprint"));
        put(f, "account_id", rs.getString("customer_account_reference"));
        put(f, "direction", rs.getString("direction"));
        put(f, "risk_level", rs.getString("risk_level"));
        put(f, "decision_v2", rs.getString("decision_v2"));
        put(f, "rule_decision", rs.getString("rule_decision"));
        f.put("sar_required", rs.getBoolean("sar_required"));
        f.put("cash_transaction", rs.getBoolean("cash_transaction"));
        put(f, "ctr_evaluation_status", rs.getString("ctr_evaluation_status"));
        put(f, "ctr_usd_equivalent", plain(rs.getBigDecimal("ctr_usd_equivalent")));
        put(f, "ctr_threshold_usd", plain(rs.getBigDecimal("ctr_threshold_usd")));
        put(f, "card_brand", rs.getString("card_brand"));
        put(f, "card_type", rs.getString("card_type"));
        put(f, "card_class", rs.getString("card_class"));
        put(f, "bill_classification_code", rs.getString("bill_classification_code"));
        put(f, "krs", nullableDouble(rs, "krs"));
        put(f, "trs", nullableDouble(rs, "trs"));
        put(f, "cra", nullableDouble(rs, "cra"));
        put(f, "legacy_model_version", rs.getString("f_model"));
        put(f, "legacy_action_taken", rs.getString("f_action"));
        f.put("legacy_source", "cloud_ingest");

        List<String> ruleNames = new ArrayList<>();
        Object rules = parseArray(rs.getString("triggered_rules"), f, "legacy_rules_unreadable");
        if (rules instanceof List<?> l) {
            l.forEach(o -> {
                if (o != null) {
                    ruleNames.add(String.valueOf(o));
                }
            });
        }
        String decision = rs.getString("a_decision") != null ? rs.getString("a_decision") : rs.getString("decision");
        if (decision == null) {
            decision = rs.getString("f_action");
        }
        Integer aScore = nullableInt(rs, "a_score");
        LocalDateTime ts = rs.getObject("eff_ts", LocalDateTime.class);
        return new LegacyRow(LegacySource.TXN, rs.getLong("txn_id"), pspOf(rs), ts, millis(ts),
                (UUID) rs.getObject("a_id"), (UUID) rs.getObject("a_edge_id"), rs.getString("a_txn_ref"), aScore,
                longList(rs.getString("a_rules")), longList(rs.getString("a_versions")), rs.getString("client_reference"),
                rs.getString("pan_hash"), rs.getString("merchant_id"), nullableLong(rs, "amount_cents"), null,
                trim(rs.getString("currency")), trim(rs.getString("merchant_country")), rs.getString("channel_type"),
                decision, nullableDouble(rs, "f_score"), rs.getBoolean("ctr_required"), List.copyOf(ruleNames), List.of(),
                f);
    }

    /** A multi-asset row with its mobile-money context, travel-rule transfer and risk signals. */
    private LegacyRow matRow(ResultSet rs, int i) throws SQLException {
        Map<String, Object> f = new LinkedHashMap<>();
        Object meta = parseObject(rs.getString("metadata"), f, "legacy_metadata_unreadable");
        if (meta instanceof Map<?, ?> m) {
            m.forEach((k, v) -> f.put(String.valueOf(k), v));
        }
        put(f, "customer_id", rs.getObject("customer_id") == null ? null : String.valueOf(rs.getLong("customer_id")));
        put(f, "asset_class", rs.getString("asset_class"));
        put(f, "product_domain", rs.getString("product_domain"));
        put(f, "source_product_domain", rs.getString("source_product_domain"));
        put(f, "destination_product_domain", rs.getString("destination_product_domain"));
        put(f, "transaction_type", rs.getString("transaction_type"));
        put(f, "fiat_equivalent_usd", plain(rs.getBigDecimal("fiat_equivalent_usd")));
        put(f, "asset_symbol", rs.getString("asset_symbol"));
        put(f, "quantity", plain(rs.getBigDecimal("quantity")));
        put(f, "unit_price", plain(rs.getBigDecimal("unit_price")));
        put(f, "device_id", rs.getString("device_fingerprint"));
        put(f, "ip_address", rs.getString("ip_address"));
        put(f, "counterparty_reference", rs.getString("counterparty_reference"));
        put(f, "funding_party_customer_id", rs.getString("funding_party_customer_id"));
        put(f, "source_network", rs.getString("source_network"));
        put(f, "destination_network", rs.getString("destination_network"));
        put(f, "originator_name", rs.getString("originator_name"));
        put(f, "originator_account", rs.getString("originator_account"));
        put(f, "beneficiary_name", rs.getString("beneficiary_name"));
        put(f, "beneficiary_account", rs.getString("beneficiary_account"));
        put(f, "merchant_category", rs.getString("merchant_category"));
        f.put("travel_rule_required", rs.getBoolean("travel_rule_required"));
        put(f, "travel_rule_status", rs.getString("travel_rule_status"));
        if (rs.getString("mm_event_type") != null) {
            put(f, "mobile_money_event_type", rs.getString("mm_event_type"));
            put(f, "account_id", rs.getString("mm_wallet"));
            put(f, "counterparty_wallet_reference", rs.getString("mm_counterparty_wallet"));
            put(f, "agent_reference", rs.getString("mm_agent"));
            put(f, "merchant_reference", rs.getString("mm_merchant"));
            put(f, "cell_id", rs.getString("mm_cell"));
            f.put("mobile_money_cross_border", rs.getBoolean("mm_cross_border"));
            if (!f.containsKey("device_id")) {
                put(f, "device_id", rs.getString("mm_device"));
            }
            put(f, "agent_device_fingerprint", rs.getString("mm_agent_device"));
            put(f, "sim_identifier_hash", rs.getString("mm_sim"));
            put(f, "latitude", plain(rs.getBigDecimal("mm_lat")));
            put(f, "longitude", plain(rs.getBigDecimal("mm_lon")));
            put(f, "agent_latitude", plain(rs.getBigDecimal("mm_agent_lat")));
            put(f, "agent_longitude", plain(rs.getBigDecimal("mm_agent_lon")));
            put(f, "sim_changed_at", iso(rs.getTimestamp("mm_sim_changed")));
            put(f, "device_registered_at", iso(rs.getTimestamp("mm_device_registered")));
            put(f, "wallet_previous_activity_at", iso(rs.getTimestamp("mm_wallet_previous")));
            put(f, "agent_float_before", plain(rs.getBigDecimal("mm_float_before")));
            put(f, "agent_float_after", plain(rs.getBigDecimal("mm_float_after")));
        }
        if (rs.getString("tr_status") != null) {
            put(f, "travel_rule_transfer_status", rs.getString("tr_status"));
            put(f, "travel_rule_jurisdiction", rs.getString("tr_jurisdiction"));
            put(f, "travel_rule_protocol", rs.getString("tr_protocol"));
            put(f, "originator_verification", rs.getString("tr_orig_verif"));
            put(f, "beneficiary_verification", rs.getString("tr_bene_verif"));
        }
        f.put("legacy_source", "multi_asset");
        String signals = rs.getString("signals");
        List<String> signalList = signals == null || signals.isBlank() ? List.of() : List.of(signals.split(","));
        LocalDateTime ts = rs.getObject("eff_ts", LocalDateTime.class);
        return new LegacyRow(LegacySource.MAT, rs.getLong("id"), pspOf(rs), ts, millis(ts), null, null,
                null, null, List.of(), List.of(), rs.getString("external_transaction_id"), null, null, null,
                rs.getBigDecimal("amount"), trim(rs.getString("currency")), trim(rs.getString("country_code")), null,
                rs.getString("decision"), (double) rs.getInt("risk_score"), false, List.of(), signalList, f);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** The run's PSP, which every selected row belongs to (joined as {@code r}). */
    private static long pspOf(ResultSet rs) throws SQLException {
        return rs.getLong("psp_id_of_run");
    }

    /** Parses a JSON object column; an unreadable value marks {@code flag} instead of failing the run. */
    private Object parseObject(String json, Map<String, Object> features, String flag) {
        return parse(json, features, flag, true);
    }

    /** Parses a JSON array column; an unreadable value marks {@code flag}. */
    private Object parseArray(String json, Map<String, Object> features, String flag) {
        return parse(json, features, flag, false);
    }

    /** Parses JSON into Java collections, recording a flag feature when the stored text is corrupt. */
    private Object parse(String json, Map<String, Object> features, String flag, boolean object) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(json);
            if (object && node.isObject()) {
                return mapper.convertValue(node, Map.class);
            }
            if (!object && node.isArray()) {
                return mapper.convertValue(node, List.class);
            }
            features.put(flag, true);
            return null;
        } catch (JsonProcessingException e) {
            features.put(flag, true);
            return null;
        }
    }

    /** JSONB list of integers (assessment rule ids), empty when absent. */
    private List<Long> longList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = mapper.readTree(json);
            List<Long> out = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(n -> {
                    if (n.canConvertToLong()) {
                        out.add(n.asLong());
                    }
                });
            }
            return List.copyOf(out);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    /** Puts a non-null value. */
    private static void put(Map<String, Object> f, String key, Object value) {
        if (value != null) {
            f.put(key, value);
        }
    }

    /** A nullable integer column. */
    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    /** A nullable bigint column. */
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    /** A nullable double column. */
    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    /** A decimal as a plain string (decimals travel as strings so no binary float rounding applies). */
    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /** A legacy wall-clock timestamp as an ISO instant string. */
    private String iso(Timestamp ts) {
        return ts == null ? null : millis(ts.toLocalDateTime()).toString();
    }

    /** A legacy wall-clock timestamp as an instant at millisecond precision (what the Edge stores). */
    private Instant millis(LocalDateTime ts) {
        return ts.atZone(zone).toInstant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** An instant as the legacy wall clock (for comparisons with legacy columns). */
    private LocalDateTime local(Instant t) {
        return LocalDateTime.ofInstant(t, zone);
    }

    /** Trimmed text or null ({@code CHAR(n)} columns come back padded). */
    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
