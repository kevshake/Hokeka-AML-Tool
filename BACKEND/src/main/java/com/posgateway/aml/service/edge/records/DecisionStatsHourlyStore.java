package com.posgateway.aml.service.edge.records;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The {@code decision_stats_hourly} rollup (V262): decision counts per PSP, Edge Site, merchant and
 * UTC hour, built from accepted decision records. It carries no amounts (decision records have
 * none; metrics v2 supplies amount sums).
 */
@Component
public class DecisionStatsHourlyStore {

    private final JdbcTemplate jdbc;

    public DecisionStatsHourlyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One rollup row. {@code merchantKey} is "" for records without a merchant. */
    public record HourlyStats(long pspId, Instant hourStart, String siteId, String merchantKey,
                              long records, long allow, long alert, long hold, long block,
                              long failClosed, long ctrRequired, long sanctionsHit,
                              long scoreSum, long latencyMsSum, int latencyMsMax) {}

    /**
     * Adds one accepted decision record to its hour bucket (upsert). Runs in the caller's
     * transaction so the rollup and the stored record commit together.
     */
    public void add(long pspId, Instant evaluatedAt, String siteId, String merchantId, String decision,
                    boolean failClosed, boolean ctrRequired, boolean sanctionsHit, long score, long latencyMs) {
        Instant hour = evaluatedAt.truncatedTo(ChronoUnit.HOURS);
        String merchantKey = merchantId == null ? "" : merchantId;
        int latency = (int) Math.min(Integer.MAX_VALUE, Math.max(0, latencyMs));
        jdbc.update("""
                INSERT INTO decision_stats_hourly (psp_id, hour_start, site_id, merchant_key, record_count,
                    allow_count, alert_count, hold_count, block_count, fail_closed_count, ctr_required_count,
                    sanctions_hit_count, score_sum, latency_ms_sum, latency_ms_max, updated_at)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (psp_id, hour_start, site_id, merchant_key) DO UPDATE SET
                    record_count = decision_stats_hourly.record_count + 1,
                    allow_count = decision_stats_hourly.allow_count + EXCLUDED.allow_count,
                    alert_count = decision_stats_hourly.alert_count + EXCLUDED.alert_count,
                    hold_count = decision_stats_hourly.hold_count + EXCLUDED.hold_count,
                    block_count = decision_stats_hourly.block_count + EXCLUDED.block_count,
                    fail_closed_count = decision_stats_hourly.fail_closed_count + EXCLUDED.fail_closed_count,
                    ctr_required_count = decision_stats_hourly.ctr_required_count + EXCLUDED.ctr_required_count,
                    sanctions_hit_count = decision_stats_hourly.sanctions_hit_count + EXCLUDED.sanctions_hit_count,
                    score_sum = decision_stats_hourly.score_sum + EXCLUDED.score_sum,
                    latency_ms_sum = decision_stats_hourly.latency_ms_sum + EXCLUDED.latency_ms_sum,
                    latency_ms_max = GREATEST(decision_stats_hourly.latency_ms_max, EXCLUDED.latency_ms_max),
                    updated_at = NOW()
                """,
                pspId, utc(hour), siteId, merchantKey,
                flag("ALLOW".equals(decision)), flag("ALERT".equals(decision)), flag("HOLD".equals(decision)),
                flag("BLOCK".equals(decision)), flag(failClosed), flag(ctrRequired), flag(sanctionsHit),
                Math.max(0, score), Math.max(0, latencyMs), latency);
    }

    /** Rollup rows of a PSP for hours in [from, to), oldest first (dashboards, R11). */
    public List<HourlyStats> range(long pspId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT psp_id, hour_start, site_id, merchant_key, record_count, allow_count, alert_count,
                       hold_count, block_count, fail_closed_count, ctr_required_count, sanctions_hit_count,
                       score_sum, latency_ms_sum, latency_ms_max
                  FROM decision_stats_hourly
                 WHERE psp_id = ? AND hour_start >= ? AND hour_start < ?
                 ORDER BY hour_start, site_id, merchant_key
                """,
                (rs, i) -> new HourlyStats(rs.getLong(1), rs.getObject(2, java.time.OffsetDateTime.class).toInstant(), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9),
                        rs.getLong(10), rs.getLong(11), rs.getLong(12), rs.getLong(13), rs.getLong(14), rs.getInt(15)),
                pspId, utc(from), utc(to));
    }

    /** JDBC parameter for a TIMESTAMPTZ column, independent of the JVM time zone. */
    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** 1 or 0, for summing a flag into a count. */
    private static int flag(boolean value) {
        return value ? 1 : 0;
    }
}
