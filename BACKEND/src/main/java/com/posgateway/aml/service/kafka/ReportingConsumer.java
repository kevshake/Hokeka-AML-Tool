package com.posgateway.aml.service.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.KafkaConfig;
import com.posgateway.aml.repository.reporting.MonthlyReportMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Reporting consumer — projects Kafka case/decision events into the
 * {@code monthly_report_metrics} table for analytics dashboards.
 *
 * <p>Each event increments one or more named metrics for the current month
 * (and PSP, if resolvable). Writes go through a race-safe Postgres
 * {@code ON CONFLICT DO UPDATE}, and per-metric aggregates are also cached
 * in Redis for low-latency dashboard reads.
 */
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ReportingConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ReportingConsumer.class);

    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private final ObjectMapper objectMapper;
    private final MonthlyReportMetricRepository metricRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public ReportingConsumer(ObjectMapper objectMapper,
                             MonthlyReportMetricRepository metricRepository,
                             RedisTemplate<String, Object> redisTemplate,
                             JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.metricRepository = metricRepository;
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_CASE_DECISION, groupId = "reporting-group")
    @Transactional
    public void handleDecisionForReporting(String message) {
        logger.debug("Reporting consumer processing Decision event");
        try {
            JsonNode root = objectMapper.readTree(message);
            Long pspId = optLong(root, "pspId");
            String decision = optStr(root, "decision");
            Long caseId = optLong(root, "caseId");
            if (caseId == null || pspId == null || decision == null || decision.isBlank()) {
                throw new IllegalArgumentException("Case decision event requires caseId, pspId and decision");
            }
            if (!claim("reporting.case-decision", caseId + ":" + decision)) {
                return;
            }
            String ym = currentYearMonth();

            // Always count one decision.
            increment(ym, pspId, "decisions.total", 1.0);
            if (decision != null && !decision.isBlank()) {
                increment(ym, pspId, "decisions." + decision.toLowerCase(), 1.0);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Reporting decision projection failed", ex);
        }
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_ALERTS_GENERATED, groupId = "reporting-group")
    @Transactional
    public void handleAlertForReporting(String message) {
        logger.debug("Reporting consumer processing Alert event");
        try {
            JsonNode root = objectMapper.readTree(message);
            Long pspId = optLong(root, "pspId");
            String severity = optStr(root, "severity");
            Long alertId = optLong(root, "alertId");
            if (alertId == null || pspId == null) {
                throw new IllegalArgumentException("Alert event requires alertId and pspId");
            }
            if (!claim("reporting.alert", String.valueOf(alertId))) {
                return;
            }
            String ym = currentYearMonth();
            increment(ym, pspId, "alerts.total", 1.0);
            if (severity != null && !severity.isBlank()) {
                increment(ym, pspId, "alerts." + severity.toLowerCase(), 1.0);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Reporting alert projection failed", ex);
        }
    }

    private boolean claim(String consumerName, String eventKey) {
        return jdbcTemplate.update("""
                INSERT INTO reporting_event_receipts (consumer_name, event_key, received_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (consumer_name, event_key) DO NOTHING
                """, consumerName, eventKey) == 1;
    }

    private void increment(String ym, Long pspId, String metric, double delta) {
        if (pspId == null) {
            // The monthly_report_metrics table requires a non-null psp_id (FK to psps).
            // Events without a resolvable tenant are skipped — no synthetic 0 row.
            logger.debug("skipping metric increment with null pspId: ym={} metric={}", ym, metric);
            return;
        }
        metricRepository.upsertIncrement(ym, pspId, metric, delta);
        try {
            String key = "monthly:report:" + ym + ":" + (pspId == null ? "all" : pspId) + ":" + metric;
            redisTemplate.delete(key);
        } catch (Exception ex) {
            logger.debug("Redis report-cache invalidation failed: {}", ex.getMessage());
        }
    }

    private static String currentYearMonth() {
        return LocalDate.now().format(YM_FMT);
    }

    private static Long optLong(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return (n != null && n.canConvertToLong()) ? n.asLong() : null;
    }

    private static String optStr(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return (n != null && !n.isNull()) ? n.asText() : null;
    }
}
