package com.posgateway.aml.integration;

import com.posgateway.aml.service.analytics.DecisionStatsService;
import com.posgateway.aml.service.edge.records.DecisionStatsHourlyStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R11 dashboard aggregates against a real PostgreSQL 16: the daily decision series, today's counts,
 * top merchants and score bands come from {@code decision_stats_hourly} and the decision records,
 * scoped per PSP, and never from the legacy {@code transactions} table.
 *
 * <p>Database: {@code -Dschema.it.jdbc-url=… -Dschema.it.user=… -Dschema.it.password=…}, otherwise a
 * Testcontainers {@code postgres:16-alpine}; skipped when neither is available.
 */
class DecisionStatsPostgresIntegrationTest {

    private static final ZoneId ZONE = ZoneOffset.UTC;
    /** Mid-day, so "today" spans hours on both sides of NOW. */
    private static final Instant NOW = Instant.parse("2026-10-17T12:30:00Z");

    private static PostgreSQLContainer<?> container;
    private static JdbcTemplate jdbc;
    private static DecisionStatsHourlyStore rollup;
    private static DecisionStatsService stats;

    /** Connects (explicit database or Testcontainers) and applies the Flyway history. */
    @BeforeAll
    @SuppressWarnings("resource")
    static void database() throws Exception {
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
        rollup = new DecisionStatsHourlyStore(jdbc);
        Constructor<DecisionStatsService> c = DecisionStatsService.class.getDeclaredConstructor(
                JdbcTemplate.class, Clock.class, ZoneId.class);
        c.setAccessible(true);
        stats = c.newInstance(jdbc, Clock.fixed(NOW, ZONE), ZONE);
    }

    /** Stops the container if this class started one. */
    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void dailySeriesTodayAndTopMerchantsComeFromTheRollupPerPsp() {
        long psp = newPsp();
        long other = newPsp();
        rollup.add(psp, NOW.minus(2, ChronoUnit.HOURS), "site-a", "M1", "ALLOW", false, false, false, 10, 4);
        rollup.add(psp, NOW.minus(1, ChronoUnit.HOURS), "site-a", "M1", "BLOCK", false, false, true, 90, 4);
        rollup.add(psp, NOW.minus(1, ChronoUnit.HOURS), "site-b", "M2", "ALERT", false, true, false, 50, 4);
        rollup.add(psp, NOW.minus(1, ChronoUnit.DAYS), "site-a", "M2", "HOLD", true, false, false, 70, 4);
        rollup.add(psp, NOW.minus(3, ChronoUnit.DAYS), "site-a", null, "ALLOW", false, false, false, 0, 4);
        rollup.add(other, NOW.minus(1, ChronoUnit.HOURS), "site-z", "M9", "BLOCK", false, false, false, 99, 4);

        DecisionStatsService.Counts today = stats.between(psp, stats.startOfToday(), NOW.plusSeconds(1));
        assertEquals(3, today.records());
        assertEquals(2, today.flagged());
        assertEquals(1, today.block());
        assertEquals(1, today.ctrRequired());
        assertEquals(1, today.sanctionsHit());

        List<DecisionStatsService.Day> days = stats.daily(psp, 7);
        assertEquals(7, days.size());
        assertEquals(3, days.get(6).counts().records(), "today is last");
        assertEquals(1, days.get(5).counts().records(), "yesterday");
        assertEquals(1, days.get(3).counts().records(), "three days ago, record without a merchant");
        assertEquals(0, days.get(0).counts().records(), "zero-filled");

        Map<String, Object> volume = stats.volumeSeries(psp, 7);
        assertEquals(List.of(0L, 0L, 0L, 1L, 0L, 1L, 3L), volume.get("data"));

        List<Map<String, Object>> top = stats.topMerchants(psp, stats.startOf(days.get(0).date()), NOW.plusSeconds(1), 5);
        assertEquals(2, top.size());
        assertEquals("M2", top.get(0).get("merchantId"), "two flagged decisions");
        assertEquals(2L, top.get(0).get("flagged"));
        assertEquals("M1", top.get(1).get("merchantId"));
        assertTrue(top.stream().noneMatch(m -> "M9".equals(m.get("merchantId"))), "other PSP never included");

        DecisionStatsService.Counts all = stats.between(null, stats.startOfToday(), NOW.plusSeconds(1));
        assertTrue(all.records() >= 4, "platform aggregate includes every PSP");
    }

    @Test
    void scoreBandsHighScoresAndRecentRecordsComeFromDecisionRecords() {
        long psp = newPsp();
        long other = newPsp();
        UUID newest = record(psp, NOW.minus(5, ChronoUnit.MINUTES), "BLOCK", 95, "M1");
        record(psp, NOW.minus(10, ChronoUnit.MINUTES), "ALERT", 65, "M1");
        record(psp, NOW.minus(20, ChronoUnit.MINUTES), "ALLOW", 40, "M2");
        record(psp, NOW.minus(30, ChronoUnit.MINUTES), "ALLOW", 5, null);
        record(psp, NOW.minus(3, ChronoUnit.DAYS), "BLOCK", 99, "M1");
        record(other, NOW.minus(5, ChronoUnit.MINUTES), "BLOCK", 99, "M9");

        Instant dayAgo = NOW.minus(24, ChronoUnit.HOURS);
        Map<String, Long> bands = stats.scoreBands(psp, dayAgo, NOW.plusSeconds(1));
        assertEquals(Map.of("low", 1L, "medium", 1L, "high", 1L, "critical", 1L), bands);
        assertEquals(1, stats.highScoreCount(psp, dayAgo, NOW.plusSeconds(1)), "65 is below 70, 95 is above");

        List<Map<String, Object>> recent = stats.recent(psp, 3);
        assertEquals(3, recent.size());
        assertEquals(newest.toString(), recent.get(0).get("edgeAssessmentId"));
        assertEquals("BLOCK", recent.get(0).get("decision"));
        assertTrue(recent.stream().allMatch(r -> ((Number) r.get("pspId")).longValue() == psp));
        assertTrue(recent.get(0).keySet().stream().noneMatch(k -> k.toLowerCase().contains("amount")
                || k.toLowerCase().contains("pan") || k.toLowerCase().contains("country")), "class-D fields only");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────────

    /** A fresh PSP row. */
    private static long newPsp() {
        String code = "R11IT_" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("INSERT INTO psps (psp_code, legal_name, country, contact_email) "
                + "VALUES (?, 'Decision stats IT', 'KEN', 'it@example.com') RETURNING psp_id", Long.class, code);
    }

    /** A v2 decision record on {@code assessments}. */
    private static UUID record(long psp, Instant evaluatedAt, String decision, int score, String merchant) {
        UUID edgeId = UUID.randomUUID();
        OffsetDateTime at = OffsetDateTime.ofInstant(evaluatedAt, ZoneOffset.UTC);
        jdbc.update("INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, edge_assessment_id, record_version, "
                        + "evaluated_at, decision, score, merchant_id, site_id, created_at) "
                        + "VALUES (?, ?, 'TXN', ?, ?, 2, ?, ?, ?, ?, 'site-a', ?)",
                UUID.randomUUID(), psp, edgeId.toString(), edgeId, at, decision, score, merchant, at);
        return edgeId;
    }
}
