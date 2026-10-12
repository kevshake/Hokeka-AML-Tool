package com.hokeka.edge.channel;

import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.activation.EdgeIdentity;
import com.hokeka.edge.crypto.EdgeKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.security.PublicKey;
import java.time.Instant;

/**
 * Pulls the sealed rule bundle from the control plane and hot-swaps it into the engine.
 *
 * <p>Outbound-only (no inbound port is opened at the PSP site): {@code GET /api/v1/edge/bundle} over
 * mTLS with {@code If-None-Match} so an unchanged bundle costs a {@code 304}. The body is an HSE-1
 * envelope wrapping the replay envelope of contract §4; it is opened, signature-verified against the
 * pinned control-plane key, replay-checked, and only then applied.
 *
 * <p>Verification happens here, in Java; the verified IR is then published into the native arena by
 * {@link EdgeEngine#loadVerifiedBundle} so the per-transaction path stays native.
 *
 * <p><b>Fail-closed:</b> any failure — transport, signature, tamper, replay, malformed IR, rejected
 * native publish — leaves the previously verified bundle running and does <b>not</b> advance the
 * {@code If-None-Match} tag, so the next poll retries instead of being answered {@code 304}. An
 * unverified bundle is never loaded, and a {@code 403} (node not ACTIVE) closes the authorization
 * gate so evaluation falls back to HOLD.
 */
@Component
@ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RuleBundlePoller {

    private static final Logger log = LoggerFactory.getLogger(RuleBundlePoller.class);

    private final ControlPlaneProperties properties;
    private final SecureChannel channel;
    private final SealedEnvelopeCodec codec;
    private final ActivationService activation;
    private final EdgeEngine engine;

    private volatile String currentVersionTag;
    private volatile Instant lastSuccess;
    private volatile String lastError = "";

    private final com.hokeka.edge.store.EdgeFeatureStore featureStore;
    private final LocalRuleBundleCopy localCopy;

    public RuleBundlePoller(ControlPlaneProperties properties, SecureChannel channel, SealedEnvelopeCodec codec,
                            ActivationService activation, EdgeEngine engine,
                            com.hokeka.edge.store.EdgeFeatureStore featureStore) {
        this.properties = properties;
        this.channel = channel;
        this.codec = codec;
        this.activation = activation;
        this.engine = engine;
        this.featureStore = featureStore;
        this.localCopy = new LocalRuleBundleCopy(properties);
    }

    /**
     * Rehydrate the last verified rule bundle from the on-prem store at start-up.
     *
     * <p>Without this a restart left the node with no bundle, so it HELD all traffic until the next
     * successful poll — an outage for the client every time the process bounced, and an indefinite one
     * if the control plane was unreachable. The bundle was already cryptographically verified before
     * it was persisted, so republishing it locally is safe; the next poll still refreshes it.
     */
    @jakarta.annotation.PostConstruct
    public void restorePersistedBundle() {
        try {
            byte[] ir = persistedIr();
            if (ir == null) {
                return;
            }
            long version = engine.loadVerifiedBundle(ir);
            currentVersionTag = "\"" + version + "\"";
            log.info("Restored persisted rule bundle v{} at start-up — enforcing immediately "
                    + "instead of holding until the first poll", version);
        } catch (Exception e) {
            log.warn("Could not restore a persisted rule bundle: {}", e.getMessage());
        }
    }

    /**
     * Prefer the on-disk copy (survives an Aerospike outage). Fall back to the feature store for
     * nodes that persisted only there before the file copy existed.
     */
    private byte[] persistedIr() {
        java.util.Optional<byte[]> file = localCopy.load();
        if (file.isPresent()) {
            return file.get();
        }
        return featureStore.loadRuleBundle().orElse(null);
    }

    public Instant lastSuccess() {
        return lastSuccess;
    }

    public String lastError() {
        return lastError;
    }

    /** One poll cycle. Never throws — the caller is a scheduler and the edge must keep serving. */
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            activation.ensureEnrolled();
            EdgeIdentity identity = activation.loadOrCreateIdentity();
            PublicKey controlPlaneSigningKey = EdgeKeys.decodeEd25519Public(properties.getSigningPublicKey());

            RestClient.RequestHeadersSpec<?> request = channel.mutualTlsClient().get()
                    .uri("/api/v1/edge/bundle")
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .header("X-Hokeka-Edge-Id", identity.edgeId());
            String tag = currentVersionTag;
            if (tag != null) {
                request = request.header("If-None-Match", tag);
            }
            ResponseEntity<byte[]> response = request.retrieve().toEntity(byte[].class);

            activation.markActive();
            if (response.getStatusCode().value() == 304) {
                lastSuccess = Instant.now();
                lastError = "";
                return;
            }
            byte[] envelope = response.getBody();
            if (envelope == null || envelope.length == 0) {
                throw new SecurityException("control plane returned an empty bundle body");
            }
            applyBundle(envelope, response.getHeaders().getETag(), identity, controlPlaneSigningKey, Instant.now());
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                activation.markUnauthorized("control plane refused this node (HTTP " + status
                        + ") — it is not ACTIVE. Approve it in the Hokeka portal.");
            }
            retain("control plane returned HTTP " + status);
        } catch (Exception e) {
            retain(e.getMessage());
        }
    }

    /**
     * Open, verify, replay-check and publish one fetched envelope.
     *
     * <p>The version tag and the success marker advance <b>only</b> when the new bundle is actually
     * live. A rejected publish must not update the {@code If-None-Match} tag: the control plane
     * would then answer {@code 304} on the next poll and the edge would sit on a stale bundle for
     * ever, believing it was current.
     *
     * @return true when the new bundle is serving traffic
     */
    boolean applyBundle(byte[] envelope, String etag, EdgeIdentity identity, PublicKey signingKey, Instant now) {
        try {
            byte[] bundleIr = codec.openPayloadBytes(envelope, activation.x25519Private(), signingKey,
                    SealedEnvelopeCodec.CTX_RULE_BUNDLE, identity.edgeId(), now);

            long previous = engine.activeVersion();
            String previousTag = currentVersionTag;
            long version = engine.loadVerifiedBundle(bundleIr);

            // The file beside the node identity is the copy the edge keeps for itself. Aerospike is
            // a second copy used for velocity history; it may be absent. Do not advance the ETag
            // until the file is durable, or the next poll is a 304 and the copy is never retried.
            featureStore.saveRuleBundle(version, bundleIr);
            if (!localCopy.save(version, bundleIr)) {
                currentVersionTag = previousTag;
                retain("verified bundle v" + version + " is live in memory but the local copy at "
                        + localCopy.path() + " was not written");
                return false;
            }

            currentVersionTag = etag != null ? etag : "\"" + version + "\"";
            lastSuccess = now;
            lastError = "";
            log.info("Rule bundle v{} verified and hot-swapped into the {} evaluator (was v{})",
                    version, engine.activeEvaluator(), previous);
            return true;
        } catch (Exception e) {
            retain(e.getMessage());
            return false;
        }
    }

    /** The {@code If-None-Match} value the next poll will send; only advances on a live publish. */
    String currentVersionTag() {
        return currentVersionTag;
    }

    private void retain(String message) {
        lastError = message == null ? "unknown error" : message;
        log.warn("Rule bundle poll failed ({}) — retaining the verified bundle v{}", lastError, engine.activeVersion());
    }
}
