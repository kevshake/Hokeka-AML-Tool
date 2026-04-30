package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleExecutionLog;
import com.posgateway.aml.repository.rules.RuleExecutionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Aggregates {@link RuleExecutionLog} rows into the effectiveness metrics
 * surfaced by GET /rules/{id}/effectiveness.
 *
 * <p>Also exposes the {@link #recordExecution} hook used by the rules
 * executor — it runs on the {@code amlTaskExecutor} thread pool so the hot
 * scoring path is not blocked by JDBC writes.
 */
@Service
public class RuleEffectivenessService {

    private static final Logger logger = LoggerFactory.getLogger(RuleEffectivenessService.class);

    private final RuleExecutionLogRepository repository;

    @Autowired
    public RuleEffectivenessService(RuleExecutionLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Compute effectiveness metrics for a single rule.
     *
     * <p>{@code falsePositiveRate = falsePositives / max(1, truePositives + falsePositives)}
     * — guards against div-by-zero and against the early period when no alerts
     * have been dispositioned yet.
     */
    public RuleEffectivenessDTO compute(Long ruleId) {
        long total = repository.countByRuleId(ruleId);
        long truePositives = repository.countByRuleIdAndDisposition(ruleId, "TRUE_POSITIVE");
        long falsePositives = repository.countByRuleIdAndDisposition(ruleId, "FALSE_POSITIVE");
        Double avgMicros = repository.averageExecutionTimeForRule(ruleId);
        Optional<Instant> lastExecuted = repository.findLastExecutedFor(ruleId);

        double avgMs = avgMicros != null ? avgMicros / 1000.0 : 0.0;
        double fpRate = (double) falsePositives / Math.max(1L, truePositives + falsePositives);

        return new RuleEffectivenessDTO(
                ruleId,
                total,
                truePositives,
                falsePositives,
                fpRate,
                avgMs,
                lastExecuted.orElse(null)
        );
    }

    /**
     * Persist a single rule-execution event. Runs asynchronously on the
     * {@code amlTaskExecutor} so the rule engine's request thread is not
     * blocked by the JDBC write.
     */
    @Async("amlTaskExecutor")
    @Transactional
    public void recordExecution(Long ruleId, Long pspId, String txnId,
                                long executionTimeMicros, RuleExecutionLog.Result result) {
        try {
            RuleExecutionLog log = new RuleExecutionLog(
                    ruleId, pspId, txnId, Instant.now(), executionTimeMicros, result);
            repository.save(log);
        } catch (Exception e) {
            // Logging only — never propagate; failed metrics writes must not affect scoring.
            logger.warn("Failed to record rule execution log for rule={} txn={}: {}",
                    ruleId, txnId, e.getMessage());
        }
    }

    /**
     * Back-fill disposition on every MATCH row for the given transaction.
     * Invoked when an alert is dispositioned by an investigator.
     */
    @Async("amlTaskExecutor")
    @Transactional
    public void recordDisposition(String txnId, String disposition) {
        if (txnId == null || disposition == null) return;
        try {
            int updated = repository.updateDispositionForMatches(txnId, disposition);
            if (updated > 0) {
                logger.debug("Back-filled disposition={} for {} rule_execution_logs (txn={})",
                        disposition, updated, txnId);
            }
        } catch (Exception e) {
            logger.warn("Failed to back-fill disposition for txn={}: {}", txnId, e.getMessage());
        }
    }

    /**
     * DTO returned by GET /rules/{id}/effectiveness.
     */
    public static class RuleEffectivenessDTO {
        private final Long ruleId;
        private final long totalExecutions;
        private final long truePositives;
        private final long falsePositives;
        private final double falsePositiveRate;
        private final double averageExecutionTimeMs;
        private final Instant lastExecuted;

        public RuleEffectivenessDTO(Long ruleId, long totalExecutions, long truePositives,
                                    long falsePositives, double falsePositiveRate,
                                    double averageExecutionTimeMs, Instant lastExecuted) {
            this.ruleId = ruleId;
            this.totalExecutions = totalExecutions;
            this.truePositives = truePositives;
            this.falsePositives = falsePositives;
            this.falsePositiveRate = falsePositiveRate;
            this.averageExecutionTimeMs = averageExecutionTimeMs;
            this.lastExecuted = lastExecuted;
        }

        public Long getRuleId() { return ruleId; }
        public long getTotalExecutions() { return totalExecutions; }
        public long getTruePositives() { return truePositives; }
        public long getFalsePositives() { return falsePositives; }
        public double getFalsePositiveRate() { return falsePositiveRate; }
        public double getAverageExecutionTimeMs() { return averageExecutionTimeMs; }
        public Instant getLastExecuted() { return lastExecuted; }
    }
}
