package com.posgateway.aml.service.analytics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dashboard aggregates built only from cloud-held decision data (Edge data residency, R11): the
 * {@code decision_stats_hourly} rollup (V262) and the class-D decision records on
 * {@code assessments}. Nothing here reads the legacy {@code transactions} table, and nothing here
 * returns an amount, currency, country, card, customer, device or IP value: decision records carry
 * none (contract section 2.2).
 *
 * <p>Every method takes a PSP id; {@code null} aggregates every PSP (platform staff only, enforced
 * by the caller). Days are calendar days in the server's zone, matching the other dashboard series.
 */
@Service
public class DecisionStatsService {

    /** Longest daily series a dashboard may ask for. */
    public static final int MAX_DAYS = 90;
    /** Most recent decision records returned at once. */
    public static final int MAX_RECENT = 50;
    /** Score at or above which a decision record counts as high risk (platform HIGH band). */
    public static final int HIGH_SCORE = 70;

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId zone;

    /** Production wiring: system clock, server zone. */
    @Autowired
    public DecisionStatsService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemDefaultZone(), ZoneId.systemDefault());
    }

    /** Test wiring with a fixed clock and zone. */
    DecisionStatsService(JdbcTemplate jdbc, Clock clock, ZoneId zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    /** Decision counts of one window (a day, or any range). */
    public record Counts(long records, long allow, long alert, long hold, long block, long failClosed,
                         long ctrRequired, long sanctionsHit, long scoreSum) {

        /** No decisions. */
        static final Counts ZERO = new Counts(0, 0, 0, 0, 0, 0, 0, 0, 0);

        /** Decisions that stopped or flagged the transaction: ALERT, HOLD or BLOCK. */
        public long flagged() {
            return alert + hold + block;
        }

        /** Mean score, 0 when there are no records. */
        public double averageScore() {
            return records == 0 ? 0.0 : (double) scoreSum / records;
        }

        /** The sum of this window and {@code o}. */
        Counts plus(Counts o) {
            return new Counts(records + o.records, allow + o.allow, alert + o.alert, hold + o.hold, block + o.block,
                    failClosed + o.failClosed, ctrRequired + o.ctrRequired, sanctionsHit + o.sanctionsHit,
                    scoreSum + o.scoreSum);
        }

        /** JSON-ready map of the counts. */
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("records", records);
            m.put("allow", allow);
            m.put("alert", alert);
            m.put("hold", hold);
            m.put("block", block);
            m.put("flagged", flagged());
            m.put("failClosed", failClosed);
            m.put("ctrRequired", ctrRequired);
            m.put("sanctionsHit", sanctionsHit);
            m.put("averageScore", Math.round(averageScore() * 10.0) / 10.0);
            return m;
        }
    }

    /** One calendar day of the series. */
    public record Day(LocalDate date, String label, Counts counts) {}

    /** Start of today in the server zone. */
    public Instant startOfToday() {
        return LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
    }

    /** Start of {@code day} in the server zone. */
    public Instant startOf(LocalDate day) {
        return day.atStartOfDay(zone).toInstant();
    }

    /** Current instant. */
    public Instant now() {
        return clock.instant();
    }

    /** Clamps a requested day count to 1..{@value #MAX_DAYS} (7 when not positive). */
    public static int clampDays(int days) {
        if (days <= 0) {
            return 7;
        }
        return Math.min(days, MAX_DAYS);
    }

    /** Decision counts of hours in [from, to). */
    public Counts between(Long pspId, Instant from, Instant to) {
        List<Object> args = new ArrayList<>();
        args.add(utc(from));
        args.add(utc(to));
        String psp = pspClause(pspId, args);
        return jdbc.query("""
                SELECT COALESCE(SUM(record_count), 0), COALESCE(SUM(allow_count), 0), COALESCE(SUM(alert_count), 0),
                       COALESCE(SUM(hold_count), 0), COALESCE(SUM(block_count), 0), COALESCE(SUM(fail_closed_count), 0),
                       COALESCE(SUM(ctr_required_count), 0), COALESCE(SUM(sanctions_hit_count), 0),
                       COALESCE(SUM(score_sum), 0)
                  FROM decision_stats_hourly
                 WHERE hour_start >= ? AND hour_start < ?""" + psp,
                rs -> rs.next() ? new Counts(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
                        rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9)) : Counts.ZERO,
                args.toArray());
    }

    /** The last {@code days} calendar days (oldest first, today last), zero-filled. */
    public List<Day> daily(Long pspId, int days) {
        int n = clampDays(days);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate first = today.minusDays(n - 1L);
        Instant from = first.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        Map<LocalDate, Counts> byDay = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            byDay.put(first.plusDays(i), Counts.ZERO);
        }
        List<Object> args = new ArrayList<>();
        args.add(utc(from));
        args.add(utc(to));
        String psp = pspClause(pspId, args);
        jdbc.query("""
                SELECT hour_start, record_count, allow_count, alert_count, hold_count, block_count, fail_closed_count,
                       ctr_required_count, sanctions_hit_count, score_sum
                  FROM decision_stats_hourly
                 WHERE hour_start >= ? AND hour_start < ?""" + psp,
                rs -> {
                    LocalDate day = rs.getObject(1, OffsetDateTime.class).toInstant().atZone(zone).toLocalDate();
                    Counts c = new Counts(rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6),
                            rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getLong(10));
                    byDay.computeIfPresent(day, (d, sum) -> sum.plus(c));
                },
                args.toArray());
        List<Day> out = new ArrayList<>(n);
        byDay.forEach((d, c) -> out.add(new Day(d, d.format(LABEL), c)));
        return out;
    }

    /** {@code {labels, data}} of daily decision record counts, the dashboards' volume series shape. */
    public Map<String, Object> volumeSeries(Long pspId, int days) {
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        for (Day d : daily(pspId, days)) {
            labels.add(d.label());
            data.add(d.counts().records());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("labels", labels);
        out.put("data", data);
        out.put("pspId", pspId);
        out.put("source", "DECISION_RECORDS");
        return out;
    }

    /**
     * Merchants with the most flagged decisions (ALERT, HOLD, BLOCK) in [from, to), most first.
     * Records without a merchant are left out.
     */
    public List<Map<String, Object>> topMerchants(Long pspId, Instant from, Instant to, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(utc(from));
        args.add(utc(to));
        String psp = pspClause(pspId, args);
        args.add(Math.max(1, Math.min(limit, 50)));
        return jdbc.query("""
                SELECT merchant_key, SUM(record_count) AS records,
                       SUM(alert_count + hold_count + block_count) AS flagged, SUM(block_count) AS blocked,
                       SUM(score_sum) AS score_sum
                  FROM decision_stats_hourly
                 WHERE hour_start >= ? AND hour_start < ? AND merchant_key <> ''""" + psp + """
                 GROUP BY merchant_key
                HAVING SUM(alert_count + hold_count + block_count) > 0
                 ORDER BY flagged DESC, records DESC, merchant_key
                 LIMIT ?""",
                (rs, i) -> {
                    long records = rs.getLong("records");
                    long flagged = rs.getLong("flagged");
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("rank", i + 1);
                    m.put("merchantId", rs.getString("merchant_key"));
                    m.put("records", records);
                    m.put("flagged", flagged);
                    m.put("blocked", rs.getLong("blocked"));
                    m.put("flaggedRate", records == 0 ? 0.0 : Math.round(flagged * 1000.0 / records) / 10.0);
                    m.put("averageScore", records == 0 ? 0.0 : Math.round(rs.getLong("score_sum") * 10.0 / records) / 10.0);
                    return m;
                },
                args.toArray());
    }

    /**
     * Decision records per score band in [from, to): low &lt; 30, medium 30..59, high 60..79,
     * critical &ge; 80. Built from the stored decision records (the rollup keeps only score sums).
     */
    public Map<String, Long> scoreBands(Long pspId, Instant from, Instant to) {
        List<Object> args = new ArrayList<>();
        args.add(utc(from));
        args.add(utc(to));
        String psp = pspClause(pspId, args);
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("low", 0L);
        out.put("medium", 0L);
        out.put("high", 0L);
        out.put("critical", 0L);
        jdbc.query("""
                SELECT CASE WHEN score >= 80 THEN 'critical' WHEN score >= 60 THEN 'high'
                            WHEN score >= 30 THEN 'medium' ELSE 'low' END AS band, COUNT(*)
                  FROM assessments
                 WHERE evaluated_at >= ? AND evaluated_at < ? AND score IS NOT NULL""" + psp + """
                 GROUP BY band""",
                rs -> {
                    out.put(rs.getString(1), rs.getLong(2));
                },
                args.toArray());
        return out;
    }

    /** Decision records with a score of at least {@value #HIGH_SCORE} in [from, to). */
    public long highScoreCount(Long pspId, Instant from, Instant to) {
        List<Object> args = new ArrayList<>();
        args.add(utc(from));
        args.add(utc(to));
        args.add(HIGH_SCORE);
        String psp = pspClause(pspId, args);
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM assessments
                 WHERE evaluated_at >= ? AND evaluated_at < ? AND score >= ?""" + psp, Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    /**
     * The most recent decision records, newest first: class-D fields only (ids, decision, score,
     * merchant, flags, site). Replaces the dashboards' "recent transactions" feed.
     */
    public List<Map<String, Object>> recent(Long pspId, int limit) {
        List<Object> args = new ArrayList<>();
        String psp = pspClause(pspId, args);
        args.add(Math.max(1, Math.min(limit, MAX_RECENT)));
        return jdbc.query("""
                SELECT edge_assessment_id, txn_ref, psp_id, site_id, edge_node_id, evaluated_at, decision, score,
                       merchant_id, ctr_required, sanctions_hit, fail_closed_code, bundle_version
                  FROM assessments
                 WHERE evaluated_at IS NOT NULL AND edge_assessment_id IS NOT NULL""" + psp + """
                 ORDER BY evaluated_at DESC, edge_assessment_id DESC
                 LIMIT ?""",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("edgeAssessmentId", rs.getString("edge_assessment_id"));
                    m.put("txnRef", rs.getString("txn_ref"));
                    m.put("pspId", rs.getLong("psp_id"));
                    m.put("siteId", rs.getString("site_id"));
                    m.put("edgeNodeId", rs.getString("edge_node_id"));
                    Timestamp at = rs.getTimestamp("evaluated_at");
                    m.put("evaluatedAt", at == null ? null : at.toInstant().toString());
                    m.put("decision", rs.getString("decision"));
                    m.put("score", rs.getObject("score"));
                    m.put("merchantId", rs.getString("merchant_id"));
                    m.put("ctrRequired", rs.getObject("ctr_required"));
                    m.put("sanctionsHit", rs.getObject("sanctions_hit"));
                    m.put("failClosedCode", rs.getString("fail_closed_code"));
                    m.put("bundleVersion", rs.getObject("bundle_version"));
                    return m;
                },
                args.toArray());
    }

    /**
     * {@code AND psp_id = ?} with its argument, or nothing for every PSP. Ends with a line break so
     * the SQL text block appended after it starts on its own line.
     */
    private static String pspClause(Long pspId, List<Object> args) {
        if (pspId == null) {
            return "\n";
        }
        args.add(pspId);
        return " AND psp_id = ?\n";
    }

    /** JDBC parameter for a TIMESTAMPTZ column, independent of the JVM time zone. */
    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
