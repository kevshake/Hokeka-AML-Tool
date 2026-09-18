package com.hokeka.edge.channel;

import com.hokeka.edge.EdgeRuleInterpreter.Decision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Accumulates edge decisions over a window into an {@link EdgeMetricsReport} — the ONLY thing the
 * edge ships to the control plane. It records <b>counts and latencies only</b>; it is structurally
 * incapable of holding a PAN / customer / amount / transaction, because {@link #record} takes only a
 * {@link Decision} and a latency. Thread-safe for the concurrent (virtual-thread) request path.
 */
@Component
public final class EdgeMetricsAggregator {

    private final String pspId;
    private final String edgeId;

    private final LongAdder total = new LongAdder();
    private final LongAdder allowed = new LongAdder();
    private final LongAdder alerted = new LongAdder();
    private final LongAdder held = new LongAdder();
    private final LongAdder blocked = new LongAdder();
    private final ConcurrentHashMap<Long, LongAdder> ruleHits = new ConcurrentHashMap<>();

    // Bounded reservoir of latency samples for percentile estimation.
    private final int reservoirCap;
    private final List<Double> latencySamples;
    private final AtomicLong latencyObserved = new AtomicLong();

    private volatile long windowStartEpochMs;

    @Autowired
    public EdgeMetricsAggregator(ControlPlaneProperties properties) {
        this(properties.getPspId(), properties.getEdgeId(), 4096, System.currentTimeMillis());
    }

    public EdgeMetricsAggregator(String pspId, String edgeId, int reservoirCap, long nowMs) {
        this.pspId = pspId;
        this.edgeId = edgeId;
        this.reservoirCap = reservoirCap;
        this.latencySamples = new ArrayList<>(reservoirCap);
        this.windowStartEpochMs = nowMs;
    }

    public void record(Decision decision, double latencyMicros) {
        total.increment();
        switch (decision.action()) {
            case ALLOW -> allowed.increment();
            case ALERT -> alerted.increment();
            case HOLD -> held.increment();
            case BLOCK -> blocked.increment();
        }
        for (Long ruleId : decision.triggeredRuleIds()) {
            ruleHits.computeIfAbsent(ruleId, k -> new LongAdder()).increment();
        }
        long n = latencyObserved.incrementAndGet();
        synchronized (latencySamples) {
            if (latencySamples.size() < reservoirCap) {
                latencySamples.add(latencyMicros);
            } else {
                // Reservoir sampling keeps a uniform sample without unbounded memory.
                int idx = (int) (Math.floorMod(n * 2654435761L, reservoirCap));
                latencySamples.set(idx, latencyMicros);
            }
        }
    }

    /** Snapshot the current window into a report and reset for the next window. */
    public EdgeMetricsReport snapshotAndReset(long bundleVersion, String bundleHash, String health, long nowMs) {
        Map<Long, Long> hits = new HashMap<>();
        for (var e : ruleHits.entrySet()) {
            hits.put(e.getKey(), e.getValue().sum());
        }
        double[] pct;
        synchronized (latencySamples) {
            pct = percentiles(new ArrayList<>(latencySamples));
        }
        long start = windowStartEpochMs;

        EdgeMetricsReport report = new EdgeMetricsReport(
                pspId, edgeId, start, nowMs,
                bundleVersion, bundleHash,
                total.sum(), allowed.sum(), alerted.sum(), held.sum(), blocked.sum(),
                hits, pct[0], pct[1], pct[2], health);

        reset(nowMs);
        return report;
    }

    private void reset(long nowMs) {
        total.reset();
        allowed.reset();
        alerted.reset();
        held.reset();
        blocked.reset();
        ruleHits.clear();
        latencyObserved.set(0);
        synchronized (latencySamples) {
            latencySamples.clear();
        }
        windowStartEpochMs = nowMs;
    }

    /** @return [p50, p95, p99] micros; zeros when there are no samples. */
    private static double[] percentiles(List<Double> samples) {
        if (samples.isEmpty()) {
            return new double[]{0, 0, 0};
        }
        samples.sort(Double::compareTo);
        return new double[]{
                quantile(samples, 0.50),
                quantile(samples, 0.95),
                quantile(samples, 0.99)
        };
    }

    private static double quantile(List<Double> sorted, double q) {
        int idx = (int) Math.ceil(q * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }
}
