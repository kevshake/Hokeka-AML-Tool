package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.crypto.EdgeKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the payload layer of the channel: HSE-1 confidentiality + authenticity, the §4 replay
 * envelope, and context binding. The "control plane" here is a locally generated keypair.
 */
class SealedEnvelopeCodecTest {

    private static final String EDGE_ID = "acme-eu-1";
    private static final String BUNDLE_IR =
            "{\"version\":41,\"psp_id\":7,\"rules\":[{\"id\":1,\"name\":\"structuring\"}]}";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-07-20T10:15:30Z");

    private SealedEnvelopeCodec codec;
    private KeyPair edgeX25519;
    private KeyPair controlPlaneEd25519;

    @BeforeEach
    void setUp() {
        codec = new SealedEnvelopeCodec(mapper, new ReplayGuard());
        edgeX25519 = EdgeKeys.generateX25519();
        controlPlaneEd25519 = EdgeKeys.generateEd25519();
    }

    private byte[] sealBundle(String edgeId, String context, Instant issuedAt) {
        return codec.seal(mapper.createObjectNode().put("raw", BUNDLE_IR), edgeId,
                EdgeKeys.x25519RawPublic(edgeX25519.getPublic()), controlPlaneEd25519.getPrivate(),
                context, issuedAt);
    }

    @Test
    void roundTripsAndNeverExposesPlaintext() {
        byte[] envelope = sealBundle(EDGE_ID, SealedEnvelopeCodec.CTX_RULE_BUNDLE, now);

        assertFalse(new String(envelope, StandardCharsets.ISO_8859_1).contains("structuring"),
                "the sealed envelope must not contain rule plaintext");

        JsonNode payload = codec.open(envelope, edgeX25519.getPrivate(), controlPlaneEd25519.getPublic(),
                SealedEnvelopeCodec.CTX_RULE_BUNDLE, EDGE_ID, now);
        assertEquals(BUNDLE_IR, payload.path("raw").asText());
    }

    @Test
    void rejectsATamperedEnvelope() {
        byte[] envelope = sealBundle(EDGE_ID, SealedEnvelopeCodec.CTX_RULE_BUNDLE, now);
        envelope[envelope.length / 2] ^= 0x01;

        assertThrows(SecurityException.class, () -> codec.open(envelope, edgeX25519.getPrivate(),
                controlPlaneEd25519.getPublic(), SealedEnvelopeCodec.CTX_RULE_BUNDLE, EDGE_ID, now));
    }

    @Test
    void rejectsAnUntrustedSigner() {
        byte[] envelope = sealBundle(EDGE_ID, SealedEnvelopeCodec.CTX_RULE_BUNDLE, now);
        KeyPair impostor = EdgeKeys.generateEd25519();

        assertThrows(SecurityException.class, () -> codec.open(envelope, edgeX25519.getPrivate(),
                impostor.getPublic(), SealedEnvelopeCodec.CTX_RULE_BUNDLE, EDGE_ID, now));
    }

    @Test
    void rejectsAnEnvelopeSealedForAnotherPurpose() {
        byte[] envelope = sealBundle(EDGE_ID, SealedEnvelopeCodec.CTX_METRICS_REPORT, now);

        assertThrows(SecurityException.class, () -> codec.open(envelope, edgeX25519.getPrivate(),
                controlPlaneEd25519.getPublic(), SealedEnvelopeCodec.CTX_RULE_BUNDLE, EDGE_ID, now));
    }

    @Test
    void rejectsAReplayedEnvelope() {
        byte[] envelope = sealBundle(EDGE_ID, SealedEnvelopeCodec.CTX_RULE_BUNDLE, now);
        codec.open(envelope, edgeX25519.getPrivate(), controlPlaneEd25519.getPublic(),
                SealedEnvelopeCodec.CTX_RULE_BUNDLE, EDGE_ID, now);

        assertThrows(SecurityException.class, () -> codec.open(envelope, edgeX25519.getPrivate(),
                controlPlaneEd25519.getPublic(), SealedEnvelopeCodec.CTX_RULE_BUNDLE, EDGE_ID, now));
    }

    @Test
    void rejectsAStaleEnvelopeAndOneAddressedElsewhere() {
        byte[] stale = sealBundle(EDGE_ID, SealedEnvelopeCodec.CTX_RULE_BUNDLE, now.minusSeconds(400));
        assertThrows(SecurityException.class, () -> codec.open(stale, edgeX25519.getPrivate(),
                controlPlaneEd25519.getPublic(), SealedEnvelopeCodec.CTX_RULE_BUNDLE, EDGE_ID, now));

        byte[] misdirected = sealBundle("other-edge", SealedEnvelopeCodec.CTX_RULE_BUNDLE, now);
        assertThrows(SecurityException.class, () -> codec.open(misdirected, edgeX25519.getPrivate(),
                controlPlaneEd25519.getPublic(), SealedEnvelopeCodec.CTX_RULE_BUNDLE, EDGE_ID, now));
    }

    @Test
    void sealsMetricsWithTheEdgeKeyAndOpensWithTheControlPlaneKey() {
        KeyPair controlPlaneX25519 = EdgeKeys.generateX25519();
        KeyPair edgeEd25519 = EdgeKeys.generateEd25519();
        EdgeMetricsReport report = new EdgeMetricsReport("acme", EDGE_ID, 0L, 1L, 41L, "sha256:beef",
                10, 7, 1, 1, 1, Map.of(100L, 3L), 12.0, 30.0, 44.0, "OK");

        byte[] envelope = codec.seal(report, EDGE_ID, EdgeKeys.x25519RawPublic(controlPlaneX25519.getPublic()),
                edgeEd25519.getPrivate(), SealedEnvelopeCodec.CTX_METRICS_REPORT, now);

        JsonNode payload = new SealedEnvelopeCodec(mapper, new ReplayGuard())
                .open(envelope, controlPlaneX25519.getPrivate(), edgeEd25519.getPublic(),
                        SealedEnvelopeCodec.CTX_METRICS_REPORT, EDGE_ID, now);
        assertEquals(10, payload.path("totalEvaluated").asLong());
        assertEquals("sha256:beef", payload.path("ruleBundleHash").asText());
    }
}
