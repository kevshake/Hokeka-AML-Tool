package com.hokeka.edge.channel;

import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.activation.EdgeIdentity;
import com.hokeka.edge.crypto.EdgeKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Ships the aggregate window report upward: {@code POST /api/v1/edge/metrics}, HSE-1 sealed to the
 * pinned control-plane X25519 key and signed with this node's Ed25519 key, inside mTLS.
 *
 * <p>The payload is an {@link EdgeMetricsReport} produced by {@link EdgeMetricsAggregator} — counts,
 * decision breakdown, rule-hit counts, latency percentiles and bundle attestation. There is no code
 * path from a transaction body to this type, so per-transaction data cannot leave the premises even
 * by mistake (contract §6).
 *
 * <p>Store-and-forward: a failed upload keeps its report in a bounded queue and retries on the next
 * window, so a control-plane outage costs no telemetry and never blocks the decision path.
 */
@Component
@ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MetricsShipper {

    private static final Logger log = LoggerFactory.getLogger(MetricsShipper.class);

    private final ControlPlaneProperties properties;
    private final SecureChannel channel;
    private final SealedEnvelopeCodec codec;
    private final ActivationService activation;
    private final EdgeMetricsAggregator aggregator;
    private final EdgeEngine engine;

    private final Deque<EdgeMetricsReport> pending = new ArrayDeque<>();
    private volatile Instant lastShipped;

    public MetricsShipper(ControlPlaneProperties properties, SecureChannel channel, SealedEnvelopeCodec codec,
                          ActivationService activation, EdgeMetricsAggregator aggregator, EdgeEngine engine) {
        this.properties = properties;
        this.channel = channel;
        this.codec = codec;
        this.activation = activation;
        this.aggregator = aggregator;
        this.engine = engine;
    }

    public Instant lastShipped() {
        return lastShipped;
    }

    public int backlog() {
        synchronized (pending) {
            return pending.size();
        }
    }

    /** Close the window and drain the buffer. Never throws. */
    public void ship() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            EdgeMetricsReport report = aggregator.snapshotAndReset(
                    engine.activeVersion(), engine.activeBundleHash(), health(), System.currentTimeMillis());
            enqueue(report);
            drain();
        } catch (Exception e) {
            log.warn("Metrics window could not be shipped ({}) — buffered {} report(s)", e.getMessage(), backlog());
        }
    }

    private String health() {
        if (!activation.authorized()) {
            return "UNAUTHORIZED";
        }
        return engine.activeVersion() < 0 ? "NO_BUNDLE" : "OK";
    }

    private void enqueue(EdgeMetricsReport report) {
        synchronized (pending) {
            while (pending.size() >= Math.max(1, properties.getMetricsBufferSize())) {
                pending.pollFirst();
            }
            pending.addLast(report);
        }
    }

    private void drain() {
        EdgeIdentity identity = activation.loadOrCreateIdentity();
        byte[] controlPlaneX25519 = EdgeKeys.decodeX25519PublicRaw(properties.getEncryptionPublicKey());

        while (true) {
            EdgeMetricsReport head;
            synchronized (pending) {
                head = pending.peekFirst();
            }
            if (head == null) {
                return;
            }
            try {
                byte[] sealed = codec.seal(head, identity.edgeId(), controlPlaneX25519,
                        activation.ed25519Private(), SealedEnvelopeCodec.CTX_METRICS_REPORT, Instant.now());
                channel.mutualTlsClient().post()
                        .uri("/api/v1/edge/metrics")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("X-Hokeka-Edge-Id", identity.edgeId())
                        .body(sealed)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                    activation.markUnauthorized("control plane rejected the metrics upload (HTTP "
                            + e.getStatusCode().value() + ")");
                }
                log.warn("Metrics upload rejected (HTTP {}) — {} report(s) buffered",
                        e.getStatusCode().value(), backlog());
                return;
            } catch (Exception e) {
                log.warn("Metrics upload failed ({}) — {} report(s) buffered", e.getMessage(), backlog());
                return;
            }
            synchronized (pending) {
                pending.pollFirst();
            }
            lastShipped = Instant.now();
        }
    }
}
