package com.hokeka.edge.residency;

import com.fasterxml.jackson.databind.JsonNode;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.activation.EdgeIdentity;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.channel.SealedEnvelopeCodec;
import com.hokeka.edge.channel.SecureChannel;
import com.hokeka.edge.crypto.EdgeKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pulls the sealed Edge config document (EDGE_DATA_RESIDENCY_CONTRACT.md §6.2) on the bundle-poll
 * cycle, and early when the tunnel delivers {@code notify config.updated} (§7.10, "within 5 s").
 *
 * <p>{@code GET /api/v1/edge/config} over mTLS with {@code If-None-Match: "<configVersion>"}; a
 * {@code 200} body is opened exactly like a bundle (§6.3 verify-then-swap): HSE-1 signature against
 * the pinned Control Plane Ed25519 key, AEAD under context {@code hokeka.edge.config}, replay check,
 * {@code edgeId} = this node. The opened document then goes through {@link EdgeConfigStore#offer},
 * which validates, appends to {@code edge_config} and swaps. The {@code If-None-Match} tag only
 * advances to a version that is actually in force, so a refused document is fetched again.
 */
public class EdgeConfigPoller {

    private static final Logger log = LoggerFactory.getLogger(EdgeConfigPoller.class);

    /** Control Plane endpoint (§6.2). */
    public static final String CONFIG_PATH = "/api/v1/edge/config";
    /** HSE-1 context of the document (§3.2). */
    public static final String CTX_EDGE_CONFIG = "hokeka.edge.config";

    private final ControlPlaneProperties properties;
    private final SecureChannel channel;
    private final SealedEnvelopeCodec codec;
    private final ActivationService activation;
    private final EdgeConfigStore store;
    private final Clock clock;
    private final AtomicBoolean soonScheduled = new AtomicBoolean();

    private volatile Instant lastSuccess;
    private volatile String lastError = "";
    private volatile boolean missingKeyLogged;

    /**
     * @param properties control-plane settings (edge id, pinned signing key)
     * @param channel    mTLS client factory
     * @param codec      replay envelope + HSE-1 codec
     * @param activation node identity and keys
     * @param store      acceptance rules and the document in force
     * @param clock      time source
     */
    public EdgeConfigPoller(ControlPlaneProperties properties, SecureChannel channel, SealedEnvelopeCodec codec,
                            ActivationService activation, EdgeConfigStore store, Clock clock) {
        this.properties = properties;
        this.channel = channel;
        this.codec = codec;
        this.activation = activation;
        this.store = store;
        this.clock = clock;
    }

    /** Last time the Control Plane answered 200/304 (null before the first success). */
    public Instant lastSuccess() {
        return lastSuccess;
    }

    /** Log-safe description of the last failure ({@code ""} after a success). */
    public String lastError() {
        return lastError;
    }

    /**
     * Schedules one poll on a virtual thread right away (the §6.2 "within 5 s of notify
     * config.updated"); a burst of notifies collapses into one extra poll.
     */
    public void pollSoon() {
        if (soonScheduled.compareAndSet(false, true)) {
            Thread.ofVirtual().name("edge-config-poll").start(() -> {
                soonScheduled.set(false);
                poll();
            });
        }
    }

    /** One poll cycle. Never throws: the caller is a scheduler and the node must keep serving. */
    public synchronized void poll() {
        if (!properties.isEnabled() || !activation.authorized()) {
            return;
        }
        String signingKey = properties.getSigningPublicKey();
        if (signingKey == null || signingKey.isBlank()) {
            if (!missingKeyLogged) {
                missingKeyLogged = true;
                log.error("CONTROLPLANE_ED25519_PUBLIC_KEY is not set: the Edge config cannot be verified, so none "
                        + "will be accepted until it is");
            }
            lastError = "CONTROLPLANE_ED25519_PUBLIC_KEY is not set";
            return;
        }
        try {
            EdgeIdentity identity = activation.loadOrCreateIdentity();
            PublicKey cpSigning = EdgeKeys.decodeEd25519Public(signingKey);
            RestClient.RequestHeadersSpec<?> request = channel.mutualTlsClient().get()
                    .uri(CONFIG_PATH)
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .header("X-Hokeka-Edge-Id", identity.edgeId());
            OptionalLong held = store.currentVersion();
            if (held.isPresent()) {
                request = request.header("If-None-Match", "\"" + held.getAsLong() + "\"");
            }
            ResponseEntity<byte[]> response = request.retrieve().toEntity(byte[].class);
            if (response.getStatusCode().value() == 304) {
                lastSuccess = clock.instant();
                lastError = "";
                return;
            }
            byte[] envelope = response.getBody();
            if (envelope == null || envelope.length == 0) {
                lastError = "control plane returned an empty config body";
                log.warn("Edge config poll: {}", lastError);
                return;
            }
            apply(envelope, identity.edgeId(), cpSigning, clock.instant());
            lastSuccess = clock.instant();
        } catch (RestClientResponseException e) {
            lastError = "control plane returned HTTP " + e.getStatusCode().value();
            log.warn("Edge config poll failed ({}) — the previous document stays in force", lastError);
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName();
            log.warn("Edge config poll failed ({}) — the previous document stays in force", lastError);
        }
    }

    /**
     * Opens and offers one fetched envelope.
     *
     * @return the store's verdict; {@code REFUSED} when the envelope does not open
     */
    EdgeConfigStore.OfferResult apply(byte[] envelope, String edgeId, PublicKey cpSigning, Instant now) {
        JsonNode document;
        try {
            document = codec.open(envelope, activation.x25519Private(), cpSigning, CTX_EDGE_CONFIG, edgeId, now);
        } catch (SecurityException e) {
            lastError = "config envelope refused (" + e.getClass().getSimpleName() + ")";
            log.warn("Edge config envelope refused: seal, replay or edge-id check failed — previous document stays in force");
            return new EdgeConfigStore.OfferResult(EdgeConfigStore.Outcome.REFUSED, "envelope refused", 0L);
        }
        EdgeConfigStore.OfferResult result = store.offer(document, now);
        lastError = result.outcome() == EdgeConfigStore.Outcome.REFUSED ? result.reason() : "";
        return result;
    }
}
