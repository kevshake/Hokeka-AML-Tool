package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.XECPublicKey;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Frame codec against the shared contract fixtures: per-frame-type sealing contexts (§3.2), the
 * §7.4 receive order (outer schema, HSE-1 open, replay, binding, body), and response item
 * discarding (§7.7).
 */
class TunnelFrameCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TunnelSchemas SCHEMAS = new TunnelSchemas();
    private static final String EDGE = "acme-eu-1";
    private static final String SESSION = "0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b";

    private final HokekaSecureEnvelope hse = new HokekaSecureEnvelope();
    private final KeyPair cpSign = gen("Ed25519");
    private final KeyPair edgeX = gen("X25519");
    private final KeyPair edgeSign = gen("Ed25519");
    private final KeyPair cpX = gen("X25519");

    /** Codec instance per side, each with its own replay guard (as on two machines). */
    private TunnelFrameCodec codec() {
        return new TunnelFrameCodec(hse, new EdgeReplayGuard(), SCHEMAS, MAPPER, 1_048_576);
    }

    private static KeyPair gen(String alg) {
        try {
            return KeyPairGenerator.getInstance(alg).generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] raw(KeyPair x) {
        return HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) x.getPublic());
    }

    /** Cloud → Edge: sealed with the CP signing key to the Edge's X25519 key. */
    private String sealToEdge(TunnelFrameCodec codec, String type, String corrId, String sessionId, JsonNode body)
            throws TunnelFrameException {
        return codec.seal(new TunnelFrameCodec.FrameSpec(type, TunnelFrameCodec.newId(), corrId, sessionId, EDGE, body),
                raw(edgeX), cpSign.getPrivate(), Instant.now());
    }

    /** Edge → cloud: sealed with the Edge signing key to the CP X25519 key. */
    private String sealToCloud(TunnelFrameCodec codec, String type, String corrId, String sessionId, JsonNode body)
            throws TunnelFrameException {
        return codec.seal(new TunnelFrameCodec.FrameSpec(type, TunnelFrameCodec.newId(), corrId, sessionId, EDGE, body),
                raw(cpX), edgeSign.getPrivate(), Instant.now());
    }

    private TunnelFrameCodec.OpenedFrame openAtEdge(TunnelFrameCodec codec, String text, String sessionId)
            throws TunnelFrameException {
        return codec.open(codec.parseOuter(text), edgeX.getPrivate(), cpSign.getPublic(), EDGE, sessionId,
                Instant.now());
    }

    private TunnelFrameCodec.OpenedFrame openAtCloud(TunnelFrameCodec codec, String text, String sessionId)
            throws TunnelFrameException {
        return codec.open(codec.parseOuter(text), cpX.getPrivate(), edgeSign.getPublic(), EDGE, sessionId,
                Instant.now());
    }

    @ParameterizedTest(name = "{0} carries {1}")
    @CsvSource({
            "request, request--txn-search.json",
            "request, request--support-access.json",
            "request, request--ledger-backfill-batch.json",
            "cancel, cancel--client-gone.json",
            "credit, credit--grant.json",
            "notify, notify--hold-place.json",
            "notify, notify--config-updated.json",
            "error, error--edge-offline.json"
    })
    void cloudToEdgeFramesRoundTripWithTheirContext(String type, String fixture) throws Exception {
        JsonNode body = ContractFixtures.fixture(fixture);
        String corrId = type.equals("cancel") || type.equals("credit") ? TunnelFrameCodec.newId() : null;
        String text = sealToEdge(codec(), type, corrId, SESSION, body);

        assertThat(SCHEMAS.validate("tunnel/frame.schema.json", MAPPER.readTree(text))).isEmpty();
        TunnelFrameCodec.OpenedFrame f = openAtEdge(codec(), text, SESSION);
        assertThat(f.type()).isEqualTo(type);
        assertThat(f.sessionId()).isEqualTo(SESSION);
        assertThat(f.body()).isEqualTo(body);
        assertThat(codec().checkBody(type, f.body())).isEmpty();
    }

    @Test
    void helloAndHelloAckRoundTrip() throws Exception {
        TunnelFrameCodec cloud = codec();
        JsonNode hello = ContractFixtures.fixture("hello--node.json");
        String helloFrame = sealToCloud(codec(), "hello", null, null, hello);
        TunnelFrameCodec.OpenedFrame opened = openAtCloud(cloud, helloFrame, null);
        assertThat(opened.sessionId()).isNull();
        assertThat(cloud.checkBody("hello", opened.body())).isEmpty();

        JsonNode ack = ContractFixtures.fixture("hello-ack--accepted.json");
        String ackFrame = sealToEdge(cloud, "hello.ack", opened.id(), SESSION, ack);
        TunnelFrameCodec.OpenedFrame ackOpened = openAtEdge(codec(), ackFrame, SESSION);
        assertThat(ackOpened.corrId()).isEqualTo(opened.id());
        assertThat(cloud.checkBody("hello.ack", ackOpened.body())).isEmpty();
    }

    @Test
    void responseAndChunkUseTheirOwnContexts() throws Exception {
        JsonNode page = ContractFixtures.fixture("response--search-page.json");
        String corr = TunnelFrameCodec.newId();
        String text = sealToCloud(codec(), "response", corr, SESSION, page);
        assertThat(openAtCloud(codec(), text, SESSION).body()).isEqualTo(page);

        // The same sealed bytes relabelled as a chunk must not open: the context differs.
        ObjectNode relabelled = (ObjectNode) MAPPER.readTree(text);
        relabelled.put("type", "chunk");
        assertThatThrownBy(() -> openAtCloud(codec(), relabelled.toString(), SESSION))
                .isInstanceOf(TunnelFrameException.class)
                .extracting(e -> ((TunnelFrameException) e).code()).isEqualTo(TunnelErrorCode.SEAL_INVALID);
    }

    @Test
    void outerFixturesParseAndInvalidOnesAreRefused() throws Exception {
        TunnelFrameCodec c = codec();
        assertThat(c.parseOuter(ContractFixtures.fixture("frame--request.json").toString()).get("type").asText())
                .isEqualTo("request");
        assertThat(c.parseOuter(ContractFixtures.fixture("frame--ping.json").toString()).get("type").asText())
                .isEqualTo("ping");
        for (String bad : new String[]{"frame--ping-with-sealed.invalid.json", "frame--request-unsealed.invalid.json",
                "frame--response-without-corrid.invalid.json"}) {
            assertThatThrownBy(() -> c.parseOuter(ContractFixtures.fixture(bad).toString()))
                    .as(bad).isInstanceOf(TunnelFrameException.class);
        }
        ObjectNode unknown = (ObjectNode) ContractFixtures.fixture("frame--ping.json");
        unknown.put("type", "telemetry");
        assertThatThrownBy(() -> c.parseOuter(unknown.toString()))
                .extracting(e -> ((TunnelFrameException) e).code()).isEqualTo(TunnelErrorCode.UNSUPPORTED_FRAME);
    }

    @Test
    void sealedPlaintextFixturesMatchWhatTheCodecBinds() {
        assertThat(SCHEMAS.validate("tunnel/sealed-plaintext.schema.json",
                ContractFixtures.fixture("sealed-plaintext--request.json"))).isEmpty();
        assertThat(SCHEMAS.validate("tunnel/sealed-plaintext.schema.json",
                ContractFixtures.fixture("sealed-plaintext--hello.json"))).isEmpty();
        assertThat(SCHEMAS.validate("tunnel/sealed-plaintext.schema.json",
                ContractFixtures.fixture("sealed-plaintext--request-without-session.invalid.json"))).isNotEmpty();
    }

    @Test
    void replayedFrameIsRefused() throws Exception {
        TunnelFrameCodec edge = codec();
        String text = sealToEdge(codec(), "request", null, SESSION, ContractFixtures.fixture("request--txn-search.json"));
        openAtEdge(edge, text, SESSION);
        assertThatThrownBy(() -> openAtEdge(edge, text, SESSION))
                .extracting(e -> ((TunnelFrameException) e).code()).isEqualTo(TunnelErrorCode.REPLAY_DETECTED);
    }

    @ParameterizedTest(name = "tampered {0}")
    @CsvSource({"id", "corrId", "ts", "nonce"})
    void outerHeaderTamperingIsABindingMismatch(String field) throws Exception {
        String corr = TunnelFrameCodec.newId();
        String text = sealToCloud(codec(), "response", corr, SESSION, ContractFixtures.fixture("response--search-page.json"));
        ObjectNode outer = (ObjectNode) MAPPER.readTree(text);
        switch (field) {
            case "id", "corrId" -> outer.put(field, TunnelFrameCodec.newId());
            case "ts" -> outer.put("ts", outer.get("ts").asLong() + 1);
            default -> outer.put("nonce", "AAAAAAAAAAAAAAAAAAAAAA");
        }
        assertThatThrownBy(() -> openAtCloud(codec(), outer.toString(), SESSION))
                .extracting(e -> ((TunnelFrameException) e).code()).isEqualTo(TunnelErrorCode.BINDING_MISMATCH);
    }

    @Test
    void wrongSessionOrEdgeIsABindingMismatch() throws Exception {
        String text = sealToCloud(codec(), "error", TunnelFrameCodec.newId(), SESSION,
                ContractFixtures.fixture("error--edge-busy.json"));
        assertThatThrownBy(() -> openAtCloud(codec(), text, TunnelFrameCodec.newId()))
                .extracting(e -> ((TunnelFrameException) e).code()).isEqualTo(TunnelErrorCode.BINDING_MISMATCH);
        assertThatThrownBy(() -> codec().open(codec().parseOuter(text), cpX.getPrivate(), edgeSign.getPublic(),
                "other-node", SESSION, Instant.now()))
                .extracting(e -> ((TunnelFrameException) e).code()).isEqualTo(TunnelErrorCode.BINDING_MISMATCH);
    }

    @Test
    void foreignSignatureOrTamperedCiphertextIsSealInvalid() throws Exception {
        String text = sealToCloud(codec(), "response", TunnelFrameCodec.newId(), SESSION,
                ContractFixtures.fixture("response--search-page.json"));
        KeyPair stranger = gen("Ed25519");
        assertThatThrownBy(() -> codec().open(codec().parseOuter(text), cpX.getPrivate(), stranger.getPublic(), EDGE,
                SESSION, Instant.now()))
                .extracting(e -> ((TunnelFrameException) e).code()).isEqualTo(TunnelErrorCode.SEAL_INVALID);

        ObjectNode outer = (ObjectNode) MAPPER.readTree(text);
        byte[] sealed = Base64.getDecoder().decode(outer.get("sealed").asText());
        sealed[60] ^= 0x01;
        outer.put("sealed", Base64.getEncoder().encodeToString(sealed));
        assertThatThrownBy(() -> openAtCloud(codec(), outer.toString(), SESSION))
                .extracting(e -> ((TunnelFrameException) e).code()).isEqualTo(TunnelErrorCode.SEAL_INVALID);
    }

    @Test
    void itemsFailingTheItemSchemaAreDiscardedByPointerOnly() throws Exception {
        ObjectNode body = (ObjectNode) ContractFixtures.fixture("response--search-item-with-pan-hash.invalid.json");
        ArrayNode items = (ArrayNode) body.get("items");
        int before = items.size();
        items.add(ContractFixtures.fixture("txn.search.item--summary.json"));
        TunnelFrameCodec.CheckedResponse checked = codec().checkResponse(body);
        assertThat(checked.discardedItems()).isEqualTo(before);
        assertThat(checked.body().get("items")).hasSize(1);
        assertThat(checked.discardedPointers()).allMatch(p -> p.startsWith("/items/0"));
        assertThat(String.join(" ", checked.discardedPointers())).doesNotContain("4a7d");

        ObjectNode envelopeBroken = (ObjectNode) ContractFixtures.fixture("response--incomplete-without-reason.invalid.json");
        assertThatThrownBy(() -> codec().checkResponse(envelopeBroken)).isInstanceOf(TunnelFrameException.class);
    }

    @Test
    void oversizeFrameIsRefusedBeforeSending() {
        TunnelFrameCodec small = new TunnelFrameCodec(hse, new EdgeReplayGuard(), SCHEMAS, MAPPER, 600);
        assertThatThrownBy(() -> small.seal(new TunnelFrameCodec.FrameSpec("request", TunnelFrameCodec.newId(), null,
                SESSION, EDGE, ContractFixtures.fixture("request--ledger-backfill-batch.json")), raw(edgeX),
                cpSign.getPrivate(), Instant.now()))
                .extracting(e -> ((TunnelFrameException) e).code()).isEqualTo(TunnelErrorCode.FRAME_TOO_LARGE);
    }

    @Test
    void pingAndPongAreUnsealedHeadersOnly() throws Exception {
        TunnelFrameCodec c = codec();
        JsonNode ping = c.parseOuter(c.ping(Instant.now()));
        JsonNode pong = c.parseOuter(c.pong(ping.get("id").asText(), Instant.now()));
        assertThat(ping.has("sealed")).isFalse();
        assertThat(pong.get("corrId").asText()).isEqualTo(ping.get("id").asText());
    }
}
