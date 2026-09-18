package com.posgateway.aml.entity.edge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persisted form of one {@link com.posgateway.aml.edge.EdgeMetricsReport} uploaded by a PSP edge
 * engine (contract §6).
 *
 * <p><b>Aggregate only.</b> There is deliberately no field on this entity — and no column in
 * {@code edge_metrics_reports} — capable of holding a PAN, PAN hash, customer identifier, IP,
 * amount or merchant id. The ingest endpoint additionally rejects any inbound JSON carrying a
 * per-transaction field, so the privacy guarantee is enforced twice: once by the wire validator and
 * once, structurally, by this schema.
 *
 * <p>Not {@code @Audited}: this is high-volume telemetry, not a security-relevant authorisation
 * record, and every row is already immutable (insert-only).
 */
@Entity
@Table(name = "edge_metrics_reports",
        indexes = {
                @Index(name = "idx_edge_metrics_psp_window", columnList = "psp_id, window_end"),
                @Index(name = "idx_edge_metrics_node", columnList = "edge_node_id, window_end")
        })
public class EdgeMetricsRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "edge_node_id", nullable = false)
    private Long edgeNodeId;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "edge_id", nullable = false, length = 128)
    private String edgeId;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "rule_bundle_version", nullable = false)
    private long ruleBundleVersion;

    @Column(name = "rule_bundle_hash", length = 128)
    private String ruleBundleHash;

    @Column(name = "total_evaluated", nullable = false)
    private long totalEvaluated;

    @Column(name = "allowed", nullable = false)
    private long allowed;

    @Column(name = "alerted", nullable = false)
    private long alerted;

    @Column(name = "held", nullable = false)
    private long held;

    @Column(name = "blocked", nullable = false)
    private long blocked;

    /** JSON object {ruleId: count}. Counts only — never a transaction reference. */
    @Column(name = "rule_hit_counts_json", columnDefinition = "TEXT")
    private String ruleHitCountsJson;

    @Column(name = "p50_latency_micros", nullable = false)
    private double p50LatencyMicros;

    @Column(name = "p95_latency_micros", nullable = false)
    private double p95LatencyMicros;

    @Column(name = "p99_latency_micros", nullable = false)
    private double p99LatencyMicros;

    @Column(name = "engine_health", length = 64)
    private String engineHealth;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEdgeNodeId() { return edgeNodeId; }
    public void setEdgeNodeId(Long edgeNodeId) { this.edgeNodeId = edgeNodeId; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getEdgeId() { return edgeId; }
    public void setEdgeId(String edgeId) { this.edgeId = edgeId; }

    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }

    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }

    public long getRuleBundleVersion() { return ruleBundleVersion; }
    public void setRuleBundleVersion(long ruleBundleVersion) { this.ruleBundleVersion = ruleBundleVersion; }

    public String getRuleBundleHash() { return ruleBundleHash; }
    public void setRuleBundleHash(String ruleBundleHash) { this.ruleBundleHash = ruleBundleHash; }

    public long getTotalEvaluated() { return totalEvaluated; }
    public void setTotalEvaluated(long totalEvaluated) { this.totalEvaluated = totalEvaluated; }

    public long getAllowed() { return allowed; }
    public void setAllowed(long allowed) { this.allowed = allowed; }

    public long getAlerted() { return alerted; }
    public void setAlerted(long alerted) { this.alerted = alerted; }

    public long getHeld() { return held; }
    public void setHeld(long held) { this.held = held; }

    public long getBlocked() { return blocked; }
    public void setBlocked(long blocked) { this.blocked = blocked; }

    public String getRuleHitCountsJson() { return ruleHitCountsJson; }
    public void setRuleHitCountsJson(String ruleHitCountsJson) { this.ruleHitCountsJson = ruleHitCountsJson; }

    public double getP50LatencyMicros() { return p50LatencyMicros; }
    public void setP50LatencyMicros(double p50LatencyMicros) { this.p50LatencyMicros = p50LatencyMicros; }

    public double getP95LatencyMicros() { return p95LatencyMicros; }
    public void setP95LatencyMicros(double p95LatencyMicros) { this.p95LatencyMicros = p95LatencyMicros; }

    public double getP99LatencyMicros() { return p99LatencyMicros; }
    public void setP99LatencyMicros(double p99LatencyMicros) { this.p99LatencyMicros = p99LatencyMicros; }

    public String getEngineHealth() { return engineHealth; }
    public void setEngineHealth(String engineHealth) { this.engineHealth = engineHealth; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
