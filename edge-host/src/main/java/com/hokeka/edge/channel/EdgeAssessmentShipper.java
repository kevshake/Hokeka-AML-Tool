package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeEvaluation;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.activation.EdgeIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ships one decision envelope per {@code /edge/evaluate} call to the Control Plane
 * ({@code POST /api/v1/edge/assessments}, mTLS), keyed by the evaluation's {@code edgeAssessmentId},
 * so the cloud can link the PSP's dual-post ingest to the decision the edge actually took.
 *
 * <p><b>What leaves the premises:</b> the assessment id, decision, score, rule ids / rule version ids
 * that fired, the bundle manifest hash and the evaluation latency. The feature snapshot is sent
 * <i>empty</i>: no PAN hash, amount, merchant or any other request field is included, preserving
 * the "transaction payloads stay on-prem" guarantee.
 *
 * <p><b>Delivery:</b> store-and-forward, at-least-once. {@link #enqueue} never blocks the decision
 * path; {@link #ship()} drains the bounded buffer on a fixed delay. The Control Plane endpoint is
 * idempotent on the id, so a retry after a lost acknowledgement is harmless. A 4xx other than
 * 401/403 means the envelope itself is unacceptable and it is dropped (logged); 401/403 marks the
 * node unauthorised; anything else stops the run and retries next tick. The buffer is in memory: if
 * the node restarts while the Control Plane is unreachable, unsent envelopes are lost and the cloud
 * keeps those assessments in "pending" (the dual-post still links the transaction).
 */
@Component
@ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EdgeAssessmentShipper {

    private static final Logger log = LoggerFactory.getLogger(EdgeAssessmentShipper.class);
    static final String ASSESSMENTS_PATH = "/api/v1/edge/assessments";

    private final ControlPlaneProperties properties;
    private final SecureChannel channel;
    private final ActivationService activation;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Deque<Map<String, Object>> pending = new ArrayDeque<>();
    private final AtomicLong dropped = new AtomicLong();

    public EdgeAssessmentShipper(ControlPlaneProperties properties, SecureChannel channel,
                                 ActivationService activation) {
        this.properties = properties;
        this.channel = channel;
        this.activation = activation;
    }

    /** Envelopes waiting to be shipped. */
    public int backlog() {
        synchronized (pending) {
            return pending.size();
        }
    }

    /** Envelopes discarded because the buffer was full or the Control Plane refused them. */
    public long dropped() {
        return dropped.get();
    }

    /** Builds the transaction-free envelope for one evaluation. */
    static Map<String, Object> envelope(EdgeEvaluation evaluation, long latencyMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("edgeAssessmentId", evaluation.edgeAssessmentId());
        body.put("bundleManifestHash", evaluation.ruleBundleHash().isBlank() ? null : evaluation.ruleBundleHash());
        List<Long> ruleIds = evaluation.triggeredRuleIds();
        List<Long> versionIds = evaluation.triggeredRuleVersionIds();
        body.put("ruleVersionIds", versionIds);
        List<Map<String, Object>> findings = new ArrayList<>();
        for (int i = 0; i < ruleIds.size(); i++) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("ruleId", ruleIds.get(i));
            f.put("ruleVersionId", versionIds.size() == ruleIds.size() ? versionIds.get(i) : null);
            f.put("action", evaluation.action().name());
            f.put("score", null);
            f.put("explanation", null);
            f.put("triggered", true);
            findings.add(f);
        }
        body.put("findings", findings);
        body.put("decision", evaluation.action().name());
        body.put("score", evaluation.score());
        body.put("featureSnapshot", Map.of());
        body.put("latencyMs", latencyMs);
        return body;
    }

    /** Queues the envelope; drops the oldest when the buffer is full. Never throws. */
    public void enqueue(EdgeEvaluation evaluation, long latencyMs) {
        if (!properties.isEnabled() || evaluation == null || evaluation.edgeAssessmentId() == null) {
            return;
        }
        Map<String, Object> body = envelope(evaluation, latencyMs);
        synchronized (pending) {
            while (pending.size() >= Math.max(1, properties.getAssessmentBufferSize())) {
                pending.pollFirst();
                dropped.incrementAndGet();
            }
            pending.addLast(body);
        }
    }

    /** Drains the buffer in order until it is empty or the Control Plane is unavailable. Never throws. */
    public void ship() {
        if (!properties.isEnabled() || !activation.authorized()) {
            return;
        }
        EdgeIdentity identity;
        try {
            identity = activation.loadOrCreateIdentity();
        } catch (Exception e) {
            log.warn("Edge identity unavailable; {} assessment envelope(s) buffered", backlog());
            return;
        }
        while (true) {
            Map<String, Object> head;
            synchronized (pending) {
                head = pending.peekFirst();
            }
            if (head == null) {
                return;
            }
            try {
                channel.mutualTlsClient().post()
                        .uri(ASSESSMENTS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hokeka-Edge-Id", identity.edgeId())
                        .body(mapper.writeValueAsString(head))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 401 || status == 403) {
                    activation.markUnauthorized("control plane rejected an assessment upload (HTTP " + status + ")");
                    log.warn("Assessment upload rejected (HTTP {}) — {} envelope(s) buffered", status, backlog());
                    return;
                }
                if (status >= 400 && status < 500) {
                    log.warn("Assessment envelope {} refused (HTTP {}); dropping it", head.get("edgeAssessmentId"), status);
                    dropped.incrementAndGet();
                    removeHead(head);
                    continue;
                }
                log.warn("Assessment upload failed (HTTP {}) — {} envelope(s) buffered", status, backlog());
                return;
            } catch (Exception e) {
                log.warn("Assessment upload failed ({}) — {} envelope(s) buffered", e.getMessage(), backlog());
                return;
            }
            removeHead(head);
        }
    }

    /** Removes the shipped head (only if it is still the same envelope). */
    private void removeHead(Map<String, Object> head) {
        synchronized (pending) {
            if (pending.peekFirst() == head) {
                pending.pollFirst();
            }
        }
    }
}
