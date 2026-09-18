package com.posgateway.aml.edge;

import java.util.Map;

/**
 * The <b>only</b> data an edge engine sends up to the Hokeka control plane: aggregate metrics about
 * a PSP's transaction processing — counts, decision breakdown, per-rule hit counts, latency
 * percentiles, and engine/rule-version health.
 *
 * <p><b>By design this record contains NO transaction or customer data</b> — no PAN, no customer id,
 * no amount, no IP, no individual transaction. The control-plane metrics-ingest endpoint validates
 * incoming reports against exactly this shape (an allow-list) and rejects anything carrying extra
 * fields, so a misconfigured or compromised edge cannot exfiltrate PII through the metrics channel.
 *
 * <p>Reports are aggregated locally at the edge over a window, buffered (store-and-forward), signed,
 * and shipped outbound. They drive billing, cross-PSP rule-effectiveness analytics, and SLA
 * dashboards centrally — all without Hokeka ever holding a PSP's raw data.
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
