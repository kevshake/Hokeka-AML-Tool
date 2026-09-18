package com.posgateway.aml.service.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.edge.EdgeMetricsReport;
import com.posgateway.aml.entity.edge.EdgeMetricsRecord;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.repository.edge.EdgeMetricsRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates and persists the aggregate metrics an edge uploads (contract §6).
 *
 * <p><b>The privacy guarantee is enforced here, twice.</b>
 * <ol>
 *   <li><i>Allow-list:</i> the inbound JSON object may contain only the field names declared by
 *       {@link EdgeMetricsReport}. Anything else — including a field the edge invented — is a hard
 *       rejection, not a silently-ignored extra.</li>
 *   <li><i>Deny-list:</i> the whole tree is additionally swept for per-transaction / PII-shaped key
 *       names (pan, customer, ip, amount, merchant, transaction, …) so a nested object can never
 *       smuggle one through.</li>
 * </ol>
 * A misconfigured or compromised edge therefore cannot exfiltrate PSP data through the metrics
 * channel: the request fails, nothing is stored.
 */
@Service
public class EdgeMetricsIngestService {

    private static final Logger log = LoggerFactory.getLogger(EdgeMetricsIngestService.class);

    /** Contract §3 — aggregate metrics upload context bytes. */
    public static final String METRICS_CONTEXT = "hokeka.metrics.report";

    /** Exactly the component names of {@link EdgeMetricsReport}. Nothing else is accepted. */
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "pspId", "edgeId",
            "windowStartEpochMs", "windowEndEpochMs",
            "ruleBundleVersion", "ruleBundleHash",
            "totalEvaluated", "allowed", "alerted", "held", "blocked",
            "ruleHitCounts",
            "p50LatencyMicros", "p95LatencyMicros", "p99LatencyMicros",
            "engineHealth");

    /**
     * Key-name fragments that can only belong to per-transaction data. Matched case-insensitively
     * anywhere in the payload tree (except inside {@code ruleHitCounts}, whose keys are rule ids).
     */
    private static final Set<String> FORBIDDEN_KEY_FRAGMENTS = Set.of(
            "pan", "card", "customer", "account", "iban", "msisdn", "phone", "email",
            "amount", "currency", "ip", "device", "merchant", "transaction", "txn",
            "reference", "name", "address", "beneficiary", "originator");

    private final EdgeMetricsRecordRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public EdgeMetricsIngestService(EdgeMetricsRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * Validate an inbound metrics payload and persist it against {@code node}.
     *
     * @throws SecurityException when the payload carries anything outside the aggregate allow-list
     */
    @Transactional
    public EdgeMetricsRecord ingest(EdgeNode node, JsonNode payload) {
        EdgeMetricsReport report = validate(node, payload);

        EdgeMetricsRecord record = new EdgeMetricsRecord();
        record.setEdgeNodeId(node.getId());
        record.setPspId(node.getPspId());
        record.setEdgeId(node.getEdgeId());
        record.setWindowStart(Instant.ofEpochMilli(report.windowStartEpochMs()));
        record.setWindowEnd(Instant.ofEpochMilli(report.windowEndEpochMs()));
        record.setRuleBundleVersion(report.ruleBundleVersion());
        record.setRuleBundleHash(report.ruleBundleHash());
        record.setTotalEvaluated(report.totalEvaluated());
        record.setAllowed(report.allowed());
        record.setAlerted(report.alerted());
        record.setHeld(report.held());
        record.setBlocked(report.blocked());
        record.setRuleHitCountsJson(writeHits(report.ruleHitCounts()));
        record.setP50LatencyMicros(report.p50LatencyMicros());
        record.setP95LatencyMicros(report.p95LatencyMicros());
        record.setP99LatencyMicros(report.p99LatencyMicros());
        record.setEngineHealth(report.engineHealth());

        EdgeMetricsRecord saved = repository.save(record);
        log.debug("Ingested edge metrics from {} (psp {}): {} evaluated, bundle v{}",
                node.getEdgeId(), node.getPspId(), report.totalEvaluated(), report.ruleBundleVersion());
        return saved;
    }

    /** Allow-list + deny-list validation, then mapping onto the aggregate record. */
    public EdgeMetricsReport validate(EdgeNode node, JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new SecurityException("metrics payload is not a JSON object");
        }
        Iterator<String> names = payload.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!ALLOWED_FIELDS.contains(name)) {
                throw new SecurityException(
                        "metrics payload carries a field outside the aggregate allow-list: " + name);
            }
        }
        rejectPerTransactionKeys(payload);

        String edgeId = payload.path("edgeId").asText(null);
        if (edgeId != null && !edgeId.equals(node.getEdgeId())) {
            throw new SecurityException("metrics payload edgeId does not match the authenticated edge");
        }

        long windowStart = payload.path("windowStartEpochMs").asLong(0);
        long windowEnd = payload.path("windowEndEpochMs").asLong(0);
        if (windowStart <= 0 || windowEnd <= 0 || windowEnd < windowStart) {
            throw new SecurityException("metrics payload has an invalid aggregation window");
        }

        Map<Long, Long> hits = new LinkedHashMap<>();
        JsonNode hitsNode = payload.get("ruleHitCounts");
        if (hitsNode != null && !hitsNode.isNull()) {
            if (!hitsNode.isObject()) {
                throw new SecurityException("ruleHitCounts must be an object of {ruleId: count}");
            }
            Iterator<Map.Entry<String, JsonNode>> it = hitsNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                long ruleId;
                try {
                    ruleId = Long.parseLong(e.getKey());
                } catch (NumberFormatException nfe) {
                    throw new SecurityException("ruleHitCounts keys must be numeric rule ids");
                }
                if (!e.getValue().isNumber()) {
                    throw new SecurityException("ruleHitCounts values must be counts");
                }
                hits.put(ruleId, e.getValue().asLong());
            }
        }

        return new EdgeMetricsReport(
                String.valueOf(node.getPspId()),
                node.getEdgeId(),
                windowStart,
                windowEnd,
                payload.path("ruleBundleVersion").asLong(0),
                payload.path("ruleBundleHash").asText(null),
                payload.path("totalEvaluated").asLong(0),
                payload.path("allowed").asLong(0),
                payload.path("alerted").asLong(0),
                payload.path("held").asLong(0),
                payload.path("blocked").asLong(0),
                hits,
                payload.path("p50LatencyMicros").asDouble(0),
                payload.path("p95LatencyMicros").asDouble(0),
                payload.path("p99LatencyMicros").asDouble(0),
                payload.path("engineHealth").asText(null));
    }

    /** Deep sweep for per-transaction / PII-shaped key names anywhere in the tree. */
    private void rejectPerTransactionKeys(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(this::rejectPerTransactionKeys);
            return;
        }
        if (!node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if ("ruleHitCounts".equals(key)) {
                continue; // keys here are numeric rule ids, values are counts — checked separately
            }
            if (!ALLOWED_FIELDS.contains(key)) {
                String lower = key.toLowerCase(Locale.ROOT);
                for (String fragment : FORBIDDEN_KEY_FRAGMENTS) {
                    if (lower.contains(fragment)) {
                        throw new SecurityException(
                                "metrics payload carries a per-transaction field: " + key);
                    }
                }
            }
            rejectPerTransactionKeys(e.getValue());
        }
    }

    private String writeHits(Map<Long, Long> hits) {
        try {
            return mapper.writeValueAsString(hits == null ? new HashMap<>() : hits);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise rule hit counts", e);
        }
    }
}
