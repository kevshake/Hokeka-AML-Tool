package com.hokeka.edge;

import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.channel.EdgeMetricsAggregator;
import com.hokeka.edge.channel.HokekaAiDecisionClient;
import com.hokeka.edge.ledger.EvaluationRecorder;
import com.hokeka.edge.ledger.LedgerEvaluation;
import com.hokeka.edge.ledger.LedgerEvaluations;
import com.hokeka.edge.ledger.LedgerProperties;
import com.hokeka.edge.store.EdgeFeatureStore;
import com.hokeka.edge.store.FeatureDerivation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Local transaction API exposed to the PSP's own API nodes over TLS 1.3. Requests are served on
 * virtual threads (see {@code spring.threads.virtual.enabled}).
 *
 * <p>Every evaluation is recorded on the site's Edge Ledger together with its decision record v2
 * in one local transaction (EDGE_DATA_RESIDENCY_CONTRACT.md §4.3); the decision record — ids and
 * outcome only — is the only per-transaction data that leaves the site, shipped later from the
 * outbox by {@code OutboxShipper}.
 */
@RestController
@RequestMapping("/edge")
public class EdgeController {

    /** 422 body for a PAN-like {@code txn_id} (contract §2.5). */
    static final Map<String, String> TXN_REF_LOOKS_LIKE_PAN = Map.of("error", "TXN_REF_LOOKS_LIKE_PAN");
    /** 422 body for a {@code txn_id} that is not 1..128 printable ASCII without spaces (contract §2.2). */
    static final Map<String, String> TXN_REF_INVALID = Map.of("error", "TXN_REF_INVALID");

    private final EdgeEngine engine;
    private final EdgeMetricsAggregator metrics;
    private final EdgeFeatureStore featureStore;
    private final HokekaAiDecisionClient aiDecisionClient;
    private final EvaluationRecorder recorder;
    private final String nodeId;
    private final String siteId;

    @Value("${featurestore.fail-closed:true}")
    private boolean failClosedOnStoreUnavailable;

    /**
     * Spring constructor: node and site ids come from {@code EDGE_EDGEID} / {@code EDGE_SITE_ID}.
     */
    @Autowired
    public EdgeController(EdgeEngine engine, EdgeMetricsAggregator metrics, EdgeFeatureStore featureStore,
                          @Autowired(required = false) HokekaAiDecisionClient aiDecisionClient,
                          EvaluationRecorder recorder, ControlPlaneProperties controlPlane,
                          LedgerProperties ledger) {
        this(engine, metrics, featureStore, aiDecisionClient, recorder, controlPlane.getEdgeId(),
                ledger.effectiveSiteId(controlPlane.getEdgeId()));
    }

    /**
     * Explicit constructor (tests, embedding).
     *
     * @param nodeId this node's edge id
     * @param siteId this node's Edge Site id
     */
    public EdgeController(EdgeEngine engine, EdgeMetricsAggregator metrics, EdgeFeatureStore featureStore,
                          HokekaAiDecisionClient aiDecisionClient, EvaluationRecorder recorder,
                          String nodeId, String siteId) {
        this.engine = engine;
        this.metrics = metrics;
        this.featureStore = featureStore;
        this.aiDecisionClient = aiDecisionClient;
        this.recorder = recorder;
        this.nodeId = nodeId == null ? "" : nodeId.trim();
        this.siteId = siteId == null ? "" : siteId.trim();
    }

    /**
     * Engine, authorization and ledger status (also surfaced through /actuator/health for the
     * engine part). {@code evaluator} reports which engine actually serves traffic, so a silent fall
     * back from the native kernel to the Java interpreter is visible at a glance; {@code ledger}
     * shows whether evaluations are being recorded (and therefore whether they fail closed).
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
        status.put("ledger", ledgerStatus());
        return status;
    }

    /** Ledger part of {@link #status()}: state, reason and counters only — no row data. */
    private Map<String, Object> ledgerStatus() {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("state", recorder.ledger().state().name());
        ledger.put("reason", recorder.ledger().stateReason());
        ledger.put("failClosed", recorder.failClosed());
        ledger.put("siteId", siteId);
        ledger.put("schemaVersion", recorder.ledger().schemaVersion());
        ledger.put("writeFailures", recorder.writeFailures());
        ledger.put("retryBacklog", recorder.retryBacklog());
        ledger.put("retryDropped", recorder.retryDropped());
        Object backlog;
        try {
            backlog = recorder.ledger().ready() ? recorder.ledger().outboxBacklog() : null;
        } catch (Exception e) {
            backlog = null;
        }
        ledger.put("outboxBacklog", backlog);
        return ledger;
    }

    /**
     * Evaluate a transaction's features → decision.
     *
     * <ol>
     *   <li>A PAN-like {@code txn_id} is refused with {@code 422 TXN_REF_LOOKS_LIKE_PAN}, and a
     *       {@code txn_id} that is not a valid reference with {@code 422 TXN_REF_INVALID}; nothing is
     *       evaluated or stored (contract §2.2, §2.5).</li>
     *   <li>The caller-supplied features are enriched with locally-derived velocity history from the
     *       on-prem store. With {@code featurestore.fail-closed=true} (default) an unavailable store
     *       yields {@code HOLD / FEATURE_STORE_UNAVAILABLE}.</li>
     *   <li>The engine decides (or a fail-closed gate answers {@code HOLD}).</li>
     *   <li>The card's velocity counters are advanced (after evaluating, so a transaction never
     *       inflates its own counters).</li>
     *   <li>The evaluation and its decision record are written to the Edge Ledger in one
     *       transaction. If that fails under {@code ledger.fail-closed=true}, the answer becomes
     *       {@code HOLD / LEDGER_UNAVAILABLE}.</li>
     * </ol>
     *
     * <p>Every evaluated response (including fail-closed HOLDs) carries a fresh
     * {@code edgeAssessmentId}.
     */
    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluate(@RequestBody Map<String, Object> features) {
        long startNanos = System.nanoTime();
        Instant evaluatedAt = Instant.now();

        switch (LedgerEvaluations.checkTxnRef(features.get("txn_id"))) {
            case LOOKS_LIKE_PAN -> {
                return ResponseEntity.unprocessableEntity().body(TXN_REF_LOOKS_LIKE_PAN);
            }
            case INVALID -> {
                return ResponseEntity.unprocessableEntity().body(TXN_REF_INVALID);
            }
            default -> {
                // ABSENT or VALID: evaluate.
            }
        }

        String panHash = features.get("pan_hash") == null ? null : String.valueOf(features.get("pan_hash"));

        FeatureDerivation derived = featureStore.deriveFeaturesDetailed(panHash);
        if (failClosedOnStoreUnavailable && derived.storeUnavailable()
                && panHash != null && !panHash.isBlank()) {
            EdgeRuleInterpreter.Decision hold = new EdgeRuleInterpreter.Decision(
                    EdgeRuleInterpreter.Action.HOLD,
                    0,
                    List.of(),
                    List.of("feature store unavailable (fail-closed): pre-auth evaluation withheld"));
            return respond(new EdgeEngine.Outcome(hold, EdgeEngine.CONTRACT_EVALUATOR_NONE,
                    "FEATURE_STORE_UNAVAILABLE"), features, panHash, evaluatedAt, startNanos);
        }

        // Caller-supplied values win over derived ones, so an integrator can override for testing
        // or supply a richer value than the local store can compute.
        Map<String, Object> enriched = new LinkedHashMap<>(derived.features());
        enriched.putAll(features);

        EdgeEngine.Outcome outcome = engine.evaluateWithProvenance(enriched);

        if (aiDecisionClient != null && outcome.failClosedCode() == null) {
            outcome = new EdgeEngine.Outcome(aiDecisionClient.enrichBorderline(outcome.decision(), enriched),
                    outcome.evaluator(), null);
        }

        // Record AFTER evaluating, so a transaction never inflates its own velocity counters.
        featureStore.recordVelocity(panHash, velocityAmount(enriched.get("amount_cents")));

        return respond(outcome, enriched, panHash, evaluatedAt, startNanos);
    }

    /**
     * Records the evaluation on the ledger (which may turn it into a fail-closed HOLD), records the
     * metrics of the decision that stands, and builds the response.
     */
    private ResponseEntity<EdgeEvaluation> respond(EdgeEngine.Outcome outcome, Map<String, Object> evaluated,
                                                   String panHash, Instant evaluatedAt, long startNanos) {
        UUID edgeAssessmentId = UUID.randomUUID();
        long version = engine.activeVersion();
        String bundleHash = engine.activeBundleHash();
        long evalMicros = Math.max(0L, (System.nanoTime() - startNanos) / 1000L);

        LedgerEvaluation row = LedgerEvaluations.fromEvaluation(edgeAssessmentId, evaluatedAt, siteId, nodeId,
                evaluated, panHash, outcome.decision(), version, bundleHash, outcome.evaluator(),
                outcome.failClosedCode(), evalMicros);
        LedgerEvaluation standing = recorder.record(row);

        EdgeRuleInterpreter.Decision decision = standing == row ? outcome.decision()
                : new EdgeRuleInterpreter.Decision(EdgeRuleInterpreter.Action.HOLD, 0, List.of(),
                        standing.reasons(), List.of());
        metrics.record(decision, (System.nanoTime() - startNanos) / 1000.0);
        return ResponseEntity.ok(EdgeEvaluation.of(decision, edgeAssessmentId.toString(), version, bundleHash));
    }

    /**
     * {@code amount_cents} for the velocity counters (minor units): a number's integral part, a
     * numeric string, or 0 when absent, negative or unusable — the same rule the store always used.
     */
    static long velocityAmount(Object value) {
        if (value instanceof Number n) {
            return Math.max(0L, n.longValue());
        }
        if (value != null) {
            try {
                return Math.max(0L, Long.parseLong(value.toString().trim()));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
