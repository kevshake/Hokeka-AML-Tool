package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.FakeNativeCore;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.activation.EdgeIdentity;
import com.hokeka.edge.crypto.EdgeKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The poll → verify → publish path. Exercised without HTTP: the transport is Spring's, the parts
 * worth proving are that the verified IR reaches the native core and that a rejected publish leaves
 * both the running bundle and the {@code If-None-Match} tag alone.
 */
class RuleBundlePollerTest {

    private static final String EDGE_ID = "acme-eu-1";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-07-20T10:15:30Z");

    private SealedEnvelopeCodec codec;
    private ActivationService activation;
    private EdgeIdentity identity;
    private KeyPair controlPlaneEd25519;
    private FakeNativeCore core;
    private EdgeEngine engine;
    private RuleBundlePoller poller;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        ControlPlaneProperties properties = new ControlPlaneProperties();
        properties.setBaseUrl("https://edge.hokeka.com");
        properties.setEdgeId(EDGE_ID);
        properties.setPspId("acme");
        properties.setIdentityFile(dir.resolve("edge-identity.json").toString());

        controlPlaneEd25519 = EdgeKeys.generateEd25519();
        properties.setSigningPublicKey(EdgeKeys.encodePublic(controlPlaneEd25519.getPublic()));

        codec = new SealedEnvelopeCodec(mapper, new ReplayGuard());
        activation = new ActivationService(properties, Optional.empty(), mapper, new MockEnvironment());
        identity = activation.loadOrCreateIdentity();
        activation.markActive();

        core = new FakeNativeCore(true, 41);
        engine = new EdgeEngine(activation, core);
        poller = new RuleBundlePoller(properties, new SecureChannel(properties), codec, activation, engine, new com.hokeka.edge.store.NoOpFeatureStore());
    }

    /** Seal a bundle the way the control plane does: for this edge's X25519 key, signed by the CP. */
    private byte[] sealedBundle(long version) {
        ObjectNode ir = mapper.createObjectNode();
        ir.put("version", version);
        ir.put("psp_id", 7);
        ir.putArray("rules");
        return codec.seal(ir, EDGE_ID, EdgeKeys.decodeX25519PublicRaw(identity.x25519PublicKey()),
                controlPlaneEd25519.getPrivate(), SealedEnvelopeCodec.CTX_RULE_BUNDLE, now);
    }

    @Test
    void publishesTheVerifiedIrIntoTheNativeCoreAndAdvancesTheTag() {
        assertNull(poller.currentVersionTag());

        assertTrue(poller.applyBundle(sealedBundle(41), "\"41\"", identity,
                controlPlaneEd25519.getPublic(), now));

        assertEquals(1, core.publishedIr().size(), "the poller must publish through loadVerifiedIr");
        assertTrue(core.publishedIr().get(0).contains("\"psp_id\""),
                "the native core must get the unwrapped IR, not the replay envelope");
        assertFalse(core.publishedIr().get(0).contains("nonce"));
        assertEquals(EdgeEngine.EVALUATOR_NATIVE, engine.activeEvaluator());
        assertEquals(41, engine.activeVersion());
        assertEquals("\"41\"", poller.currentVersionTag());
    }

    @Test
    void aRejectedNativePublishAdvancesNeitherTheVersionNorTheEtag() {
        poller.applyBundle(sealedBundle(41), "\"41\"", identity, controlPlaneEd25519.getPublic(), now);

        core.rejectNextPublish();
        assertFalse(poller.applyBundle(sealedBundle(42), "\"42\"", identity,
                controlPlaneEd25519.getPublic(), now));

        assertEquals(41, engine.activeVersion(), "the previous bundle must keep serving");
        assertEquals("\"41\"", poller.currentVersionTag(),
                "advancing the tag would make the control plane answer 304 and strand the edge on a stale bundle");
        assertTrue(poller.lastError().contains("still serving the previous bundle"), poller.lastError());
    }

    @Test
    void aTamperedBundleIsNeverHandedToTheNativeCore() {
        byte[] envelope = sealedBundle(41);
        envelope[envelope.length / 2] ^= 0x01;

        assertFalse(poller.applyBundle(envelope, "\"41\"", identity, controlPlaneEd25519.getPublic(), now));

        assertTrue(core.publishedIr().isEmpty(), "an unverified bundle must never reach the arena");
        assertEquals(-1, engine.activeVersion());
        assertNull(poller.currentVersionTag());
    }

    @Test
    void aReplayedBundleIsRejectedAndTheTagIsUnchanged() {
        byte[] envelope = sealedBundle(41);
        assertTrue(poller.applyBundle(envelope, "\"41\"", identity, controlPlaneEd25519.getPublic(), now));

        assertFalse(poller.applyBundle(envelope, "\"41-replayed\"", identity,
                controlPlaneEd25519.getPublic(), now));
        assertEquals("\"41\"", poller.currentVersionTag());
        assertEquals(1, core.publishedIr().size());
    }
}
