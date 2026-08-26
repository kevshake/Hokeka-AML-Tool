package com.hokeka.edge;

import com.hokeka.edge.channel.EdgeMetricsAggregator;
import com.hokeka.edge.store.EdgeFeatureStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local transaction API exposed to the PSP's own API nodes over TLS 1.3. Requests are served on
 * virtual threads (see {@code spring.threads.virtual.enabled}). No transaction data leaves the edge;
 * only the aggregate counters recorded here are ever shipped upward.
 */
@RestController
@RequestMapping("/edge")
public class EdgeController {

    private final EdgeEngine engine;
    private final EdgeMetricsAggregator metrics;
    private final EdgeFeatureStore featureStore;

    public EdgeController(EdgeEngine engine, EdgeMetricsAggregator metrics,
                          EdgeFeatureStore featureStore) {
        this.engine = engine;
        this.metrics = metrics;
        this.featureStore = featureStore;
    }

    /**
     * Engine + authorization status (also surfaced through /actuator/health). {@code evaluator}
     * reports which engine actually serves traffic, so a silent fall back from the native kernel to
     * the Java interpreter is visible at a glance.
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("evaluator", engine.activeEvaluator());
        status.put("nativeCore", engine.nativeAvailable() ? "loaded" : "absent");
        status.put("nativeDegraded", engine.nativeDegraded());
        status.put("nativeDegradedReason", engine.nativeDegradedReason());
        status.put("standbyBundleReady", engine.standbyReady());
        status.put("ruleBundleVersion", engine.activeVersion());
        status.put("ruleBundleHash", engine.activeBundleHash());
        status.put("authorization", engine.authorizationState());
        status.put("authorizationReason", engine.authorizationReason());
        // Visible so a node running WITHOUT local history (velocity rules cannot fire) is not
        // mistaken for a healthy one.
        status.put("featureStore", featureStore.available() ? "connected" : "unavailable");
        return status;
    }

    /**
     * Evaluate a transaction's features → decision. HOLD until the node is authorized.
     *
     * <p>The caller-supplied features are enriched with locally-derived history (velocity counts and
     * amount sums for the card) read from the on-prem store, so the Rust core checks the transaction
     * against what this card did before — not just the single request body. The transaction is then
     * recorded locally so it counts toward future evaluations. Both steps are fail-soft: if the store
     * is unavailable the decision is still made, on the caller-supplied features alone.
     */
    @PostMapping("/evaluate")
    public ResponseEntity<EdgeRuleInterpreter.Decision> evaluate(@RequestBody Map<String, Object> features) {
        long startNanos = System.nanoTime();

        String panHash = features.get("pan_hash") == null ? null : String.valueOf(features.get("pan_hash"));

        // Caller-supplied values win over derived ones, so an integrator can override for testing
        // or supply a richer value than the local store can compute.
        Map<String, Object> enriched = new java.util.LinkedHashMap<>(featureStore.deriveFeatures(panHash));
        enriched.putAll(features);

        EdgeRuleInterpreter.Decision decision = engine.evaluate(enriched);

        // Record AFTER evaluating, so a transaction never inflates its own velocity counters.
        featureStore.recordTransaction(panHash, enriched, decision);

        metrics.record(decision, (System.nanoTime() - startNanos) / 1000.0);
        return ResponseEntity.ok(decision);
    }
}
