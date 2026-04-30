package com.posgateway.aml.metrics;

import com.posgateway.aml.service.graph.Neo4jGdsService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prometheus Metrics Exporter for AML Graph Analytics.
 * 
 * Exports metrics for Grafana dashboards:
 * - Graph analytics (PageRank, Communities, Hubs)
 * - ML scoring distribution
 * - Drools rule triggers
 * - Transaction decisions (ALLOW/HOLD/BLOCK)
 * - CBK/FATF compliance metrics
 */
@Component
public class GraphMetricsExporter {

    private static final Logger logger = LoggerFactory.getLogger(GraphMetricsExporter.class);

    private final MeterRegistry meterRegistry;
    private final Neo4jGdsService neo4jGdsService;

    // Atomic counters for real-time metrics
    private final AtomicInteger highRiskCommunities = new AtomicInteger(0);
    private final AtomicInteger suspiciousHubs = new AtomicInteger(0);
    private final AtomicLong totalTransactionsScored = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> decisionCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> ruleTriggeredCounts = new ConcurrentHashMap<>();

    // Score distribution buckets
    private final AtomicLong scoreLow = new AtomicLong(0); // 0.0 - 0.3
    private final AtomicLong scoreMedium = new AtomicLong(0); // 0.3 - 0.7
    private final AtomicLong scoreHigh = new AtomicLong(0); // 0.7 - 0.9
    private final AtomicLong scoreCritical = new AtomicLong(0); // 0.9 - 1.0

    @Autowired
    public GraphMetricsExporter(MeterRegistry meterRegistry,
            @Autowired(required = false) Neo4jGdsService neo4jGdsService) {
        this.meterRegistry = meterRegistry;
        this.neo4jGdsService = neo4jGdsService;
        initializeMetrics();
    }

    private void initializeMetrics() {
        // Graph Analytics Gauges
        Gauge.builder("aml_high_risk_communities", highRiskCommunities, AtomicInteger::get)
                .description("Number of high-risk transaction communities detected")
                .tag("source", "neo4j_gds")
                .register(meterRegistry);

        Gauge.builder("aml_suspicious_hubs", suspiciousHubs, AtomicInteger::get)
                .description("Number of suspicious hub merchants (high betweenness)")
                .tag("source", "neo4j_gds")
                .register(meterRegistry);

        Gauge.builder("aml_total_transactions_scored", totalTransactionsScored, AtomicLong::get)
                .description("Total transactions scored by ML model")
                .register(meterRegistry);

        // ML Score Distribution
        Gauge.builder("aml_score_distribution", scoreLow, AtomicLong::get)
                .description("Transactions with low risk score (0-0.3)")
                .tag("bucket", "low")
                .register(meterRegistry);

        Gauge.builder("aml_score_distribution", scoreMedium, AtomicLong::get)
                .description("Transactions with medium risk score (0.3-0.7)")
                .tag("bucket", "medium")
                .register(meterRegistry);

        Gauge.builder("aml_score_distribution", scoreHigh, AtomicLong::get)
                .description("Transactions with high risk score (0.7-0.9)")
                .tag("bucket", "high")
                .register(meterRegistry);

        Gauge.builder("aml_score_distribution", scoreCritical, AtomicLong::get)
                .description("Transactions with critical risk score (0.9-1.0)")
                .tag("bucket", "critical")
                .register(meterRegistry);

        // Decision counters
        for (String decision : new String[] { "ALLOW", "HOLD", "BLOCK" }) {
            decisionCounts.put(decision, new AtomicLong(0));
            Gauge.builder("aml_decision_count", decisionCounts.get(decision), AtomicLong::get)
                    .description("Count of " + decision + " decisions")
                    .tag("decision", decision)
                    .register(meterRegistry);
        }

        // Common rule triggers
        for (String rule : new String[] {
                "CTR_THRESHOLD_10K", "SAR_STRUCTURING_DETECTION", "OFAC_HIGH_RISK_COUNTRY",
                "ML_SCORE_HIGH_RISK", "ML_SCORE_MEDIUM_RISK", "VELOCITY_BREACH_1H",
                "HIGH_BETWEENNESS_HUB", "HIGH_INFLUENCE_HIGH_VALUE"
        }) {
            ruleTriggeredCounts.put(rule, new AtomicLong(0));
            Gauge.builder("aml_drools_rule_triggers", ruleTriggeredCounts.get(rule), AtomicLong::get)
                    .description("Number of times rule was triggered")
                    .tag("rule", rule)
                    .register(meterRegistry);
        }

        logger.info("Graph analytics Prometheus metrics initialized");
    }

    // =========================================================================
    // PUBLIC METHODS - Called by other services to record metrics
    // =========================================================================

    /**
     * Record a transaction scoring event with its ML score.
     */
    public void recordScoring(Long txnId, Double mlScore, String decision,
            java.util.List<String> triggeredRules) {
        totalTransactionsScored.incrementAndGet();

        // Update score distribution
        if (mlScore <= 0.3) {
            scoreLow.incrementAndGet();
        } else if (mlScore <= 0.7) {
            scoreMedium.incrementAndGet();
        } else if (mlScore <= 0.9) {
            scoreHigh.incrementAndGet();
        } else {
            scoreCritical.incrementAndGet();
        }

        // Update decision count
        AtomicLong counter = decisionCounts.get(decision);
        if (counter != null) {
            counter.incrementAndGet();
        }

        // Update rule triggers
        if (triggeredRules != null) {
            for (String rule : triggeredRules) {
                AtomicLong ruleCounter = ruleTriggeredCounts.get(rule);
                if (ruleCounter != null) {
                    ruleCounter.incrementAndGet();
                }
            }
        }
    }

    /**
     * Update high-risk community count.
     */
    public void updateHighRiskCommunities(int count) {
        highRiskCommunities.set(count);
    }

    /**
     * Update suspicious hub count.
     */
    public void updateSuspiciousHubs(int count) {
        suspiciousHubs.set(count);
    }

    /**
     * Record a CTR submission.
     */
    public void recordCtr(Long txnId) {
        Counter.builder("aml_ctr_submissions")
                .description("CTR submissions count")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record an STR submission.
     */
    public void recordStr(Long txnId) {
        Counter.builder("aml_str_submissions")
                .description("STR submissions count")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record scoring latency.
     */
    public void recordScoringLatency(long latencyMs) {
        Timer.builder("aml_scoring_latency")
                .description("ML scoring latency in milliseconds")
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(latencyMs));
    }

    // =========================================================================
    // SCHEDULED REFRESH - Updates graph analytics metrics periodically
    // =========================================================================

    /**
     * Refresh graph analytics metrics from Neo4j GDS.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRateString = "${metrics.graph.refresh.interval:300000}")
    public void refreshGraphMetrics() {
        if (neo4jGdsService == null) {
            logger.debug("Neo4j GDS not available, skipping graph metrics refresh");
            return;
        }

        try {
            logger.debug("Refreshing graph analytics metrics from Neo4j GDS");

            // Query actual metrics from Neo4j GDS service
            int riskyCommunities = neo4jGdsService.countHighRiskCommunities();
            int riskyHubs = neo4jGdsService.countSuspiciousHubs();

            // Update atomic counters
            highRiskCommunities.set(riskyCommunities);
            suspiciousHubs.set(riskyHubs);

            logger.info("Updated graph metrics: {} high-risk communities, {} suspicious hubs",
                    riskyCommunities, riskyHubs);

        } catch (Exception e) {
            logger.error("Error refreshing graph metrics: {}", e.getMessage());
        }
    }

    /**
     * Get current metrics summary for logging.
     */
    public Map<String, Object> getMetricsSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        summary.put("totalScored", totalTransactionsScored.get());
        summary.put("scoreLow", scoreLow.get());
        summary.put("scoreMedium", scoreMedium.get());
        summary.put("scoreHigh", scoreHigh.get());
        summary.put("scoreCritical", scoreCritical.get());
        summary.put("decisionsAllow", decisionCounts.getOrDefault("ALLOW", new AtomicLong(0)).get());
        summary.put("decisionsHold", decisionCounts.getOrDefault("HOLD", new AtomicLong(0)).get());
        summary.put("decisionsBlock", decisionCounts.getOrDefault("BLOCK", new AtomicLong(0)).get());
        summary.put("highRiskCommunities", highRiskCommunities.get());
        summary.put("suspiciousHubs", suspiciousHubs.get());
        return summary;
    }
}
