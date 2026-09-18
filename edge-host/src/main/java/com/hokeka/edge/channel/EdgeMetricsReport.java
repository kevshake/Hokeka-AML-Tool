package com.hokeka.edge.channel;

import java.util.Map;

/**
 * The <b>only</b> shape the edge is able to ship upward (channel contract §6). Counts, decision
 * breakdown, per-rule hit counts, latency percentiles and attestation of the running bundle — and
 * nothing else.
 *
 * <p>Privacy is structural, not procedural: the record has no field that could carry a PAN, PAN
 * hash, customer identifier, IP, amount or merchant id, and the only way to populate it is
 * {@link EdgeMetricsAggregator#record}, which accepts a decision and a latency. There is no code
 * path from a transaction body into this type.
 */
public record EdgeMetricsReport(
        String pspId,
        String edgeId,
        long windowStartEpochMs,
        long windowEndEpochMs,

        long ruleBundleVersion,
        String ruleBundleHash,

        long totalEvaluated,
        long allowed,
        long alerted,
        long held,
        long blocked,

        /** ruleId -> times triggered in the window (counts only). */
        Map<Long, Long> ruleHitCounts,

        double p50LatencyMicros,
        double p95LatencyMicros,
        double p99LatencyMicros,

        String engineHealth
) {
    public long totalDecisions() {
        return allowed + alerted + held + blocked;
    }
}
