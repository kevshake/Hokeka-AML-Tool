package com.posgateway.aml.service.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.KafkaConfig;
import com.posgateway.aml.repository.reporting.MonthlyReportMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
    private static final Duration CACHE_TTL = Duration.ofHours(2);

    private final ObjectMapper objectMapper;
    private final MonthlyReportMetricRepository metricRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public ReportingConsumer(ObjectMapper objectMapper,
                             MonthlyReportMetricRepository metricRepository,
                             RedisTemplate<String, Object> redisTemplate) {
        this.objectMapper = objectMapper;
        this.metricRepository = metricRepository;
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_CASE_DECISION, groupId = "reporting-group")
    @Transactional
    public void handleDecisionForReporting(String message) {
        logger.debug("Reporting consumer processing Decision event");
        try {
            JsonNode root = objectMapper.readTree(message);
            Long pspId = optLong(root, "pspId");
            String decision = optStr(root, "decision");
            String ym = currentYearMonth();

            // Always count one decision.
            increment(ym, pspId, "decisions.total", 1.0);
            if (decision != null && !decision.isBlank()) {
                increment(ym, pspId, "decisions." + decision.toLowerCase(), 1.0);
            }
        } catch (Exception ex) {
            logger.error("Reporting decision handler failed: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_COMPLIANCE_ALERT, groupId = "reporting-group")
    @Transactional
    public void handleAlertForReporting(String message) {
        logger.debug("Reporting consumer processing Alert event");
        try {
            JsonNode root = objectMapper.readTree(message);
            Long pspId = optLong(root, "pspId");
            String severity = optStr(root, "severity");
            String ym = currentYearMonth();
            increment(ym, pspId, "alerts.total", 1.0);
            if (severity != null && !severity.isBlank()) {
                increment(ym, pspId, "alerts." + severity.toLowerCase(), 1.0);
            }
        } catch (Exception ex) {
            logger.error("Reporting alert handler failed: {}", ex.getMessage());
        }
    }

    private void increment(String ym, Long pspId, String metric, double delta) {
        try {
            metricRepository.upsertIncrement(ym, pspId, metric, delta);
        } catch (DataAccessException ex) {
            logger.warn("Metric upsert failed (table missing?). " +
                    "FIXME(go-live, monthly_report_metrics-migration-pending). err={}",
                    ex.getMessage());
            return;
        }
        try {
            String key = "monthly:report:" + ym + ":" + (pspId == null ? "all" : pspId) + ":" + metric;
            redisTemplate.opsForValue().increment(key, delta);
            redisTemplate.expire(key, CACHE_TTL);
        } catch (Exception ex) {
            logger.debug("Redis increment failed: {}", ex.getMessage());
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
