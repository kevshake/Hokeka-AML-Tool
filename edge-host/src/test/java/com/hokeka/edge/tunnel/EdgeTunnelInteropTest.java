package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.activation.AuthorizationGate;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.channel.SecureChannel;
import com.hokeka.edge.ledger.LedgerTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.security.KeyPair;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end interop of the Edge Tunnel client (contract §7) against a minimal in-test WebSocket
 * server over TLS 1.3 with a mandatory client certificate, whose Control Plane side seals and opens
 * frames with {@link CloudSide} — an independent implementation of the BACKEND's scheme. Every
 * frame either side produces is checked against the shared schemas, and the bodies the cloud sends
 * start from the shared fixtures.
 */
class EdgeTunnelInteropTest {

    @TempDir
    static Path tlsDir;

    private static TestTls tls;

    private TunnelRig rig;
    private FakeTunnelServer server;
    private CloudSide cloud;
    private EdgeTunnelClient client;
    private TunnelProperties props;
    private final AtomicInteger configPolls = new AtomicInteger();
    private final AtomicInteger bundlePolls = new AtomicInteger();
    private final AtomicReference<Instant> bundleOk = new AtomicReference<>();
    private volatile int ackMaxInflight = 16;

    @BeforeAll
    static void tlsMaterial() throws Exception {
        tls = new TestTls(tlsDir, TunnelRig.EDGE_ID);
    }

    @BeforeEach
    void setUp() throws Exception {
        rig = new TunnelRig();
        rig.acceptConfig(14, TunnelRig::allowAllOps);
        server = new FakeTunnelServer(tls.serverContext());
        KeyPair edgeX = CloudSide.generate("X25519");
        KeyPair edgeEd = CloudSide.generate("Ed25519");
        cloud = new CloudSide(TunnelRig.EDGE_ID, edgeX.getPublic(), edgeEd.getPublic());

        ControlPlaneProperties cp = new ControlPlaneProperties();
        cp.setBaseUrl("https://localhost:" + server.port());
        cp.setEdgeId(TunnelRig.EDGE_ID);
        cp.setKeystore(tls.clientKeystore.toString());
        cp.setKeystorePassword(TestTls.PASSWORD);
        cp.setTruststore(tls.clientTruststore.toString());
        cp.setTruststorePassword(TestTls.PASSWORD);
        cp.setRequestTimeout(Duration.ofSeconds(5));
        SecureChannel channel = new SecureChannel(cp);

        props = new TunnelProperties();
        props.setBackoffMin(Duration.ofMillis(50));
        props.setBackoffMax(Duration.ofMillis(200));
        props.setGateRecheckInterval(Duration.ofMillis(100));
        props.setHelloTimeout(Duration.ofSeconds(2));
        props.setSupersededHoldOff(Duration.ofSeconds(2));
        props.setCreditStallTimeout(Duration.ofMillis(1500));
        props.setAgentVersion("2.4.0");

        EdgeTunnelClient.Keys keys = new EdgeTunnelClient.Keys(CloudSide.raw(
                (java.security.interfaces.XECPublicKey) cloud.cpX25519.getPublic()), cloud.cpEd25519.getPublic(),
                edgeX.getPrivate(), edgeEd.getPrivate());
        AuthorizationGate gate = new AuthorizationGate() {
            @Override
            public boolean authorized() {
                return true;
            }

            @Override
            public String state() {
                return STATE_ACTIVE;
            }

            @Override
            public String reason() {
                return "";
            }
        };
        EdgeTunnelClient.Signals signals = new EdgeTunnelClient.Signals(configPolls::incrementAndGet,
                bundlePolls::incrementAndGet, bundleOk::get, () -> -1L, () -> 2);
        URI uri = EdgeTunnelClient.tunnelUri("", cp.getBaseUrl());
        client = new EdgeTunnelClient(props, uri, new EdgeTunnelClient.Identity(TunnelRig.EDGE_ID, TunnelRig.SITE_ID,
                TunnelRig.PSP_ID), () -> keys, channel::mutualTlsHttpClient, gate, rig.codec, rig.ops, rig.accessLog,
                rig.config, rig.holds, signals, rig.mapper, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() throws Exception {
        client.stop();
        server.close();
        rig.close();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** Accepts the next upgrade, checks hello and answers hello.ack. Returns the session and its id. */
    private Live handshake() throws Exception {
        FakeTunnelServer.Session s = server.sessions.poll(15, TimeUnit.SECONDS);
        assertNotNull(s, "the Edge connects");
        CloudSide.Received hello = cloud.open(s.next(10), null);
        assertEquals("hello", hello.type());
        assertTrue(rig.schemas.violations("tunnel/hello.schema.json", hello.body()).isEmpty(), hello.body().toString());
        String sessionId = UUID.randomUUID().toString();
        ObjectNode ack = rig.fixture("hello-ack--accepted.json");
        ack.put("sessionId", sessionId);
        ArrayNode ops = ack.putArray("opsEffective");
        TunnelOps.IMPLEMENTED.forEach(ops::add);
        ack.put("maxInflight", ackMaxInflight);
        ack.put("heartbeatIntervalMs", 5000);
        ack.put("futureMember", "ignored by this version");
        s.sendText(cloud.seal("hello.ack", CloudSide.id(), hello.id(), sessionId, ack, Instant.now()));
        await(() -> client.state() == EdgeTunnelClient.State.OPEN && sessionId.equals(client.sessionId()), 10);
        return new Live(s, sessionId, hello);
    }

    /** One open session as the cloud sees it. */
    private record Live(FakeTunnelServer.Session s, String sessionId, CloudSide.Received hello) {
    }

    /** Next sealed (non-ping) frame from the Edge, opened by the cloud. */
    private CloudSide.Received next(Live live) throws Exception {
        while (true) {
            String text = live.s().next(10);
            assertNotNull(text, "expected a frame from the Edge");
            assertTrue(rig.schemas.violations("tunnel/frame.schema.json", rig.mapper.readTree(text)).isEmpty());
            CloudSide.Received r = cloud.open(text, live.sessionId());
            if (!r.type().equals("ping") && !r.type().equals("pong")) {
                if (r.type().equals("response") || r.type().equals("chunk")) {
                    assertTrue(rig.schemas.violations("tunnel/response.schema.json", r.body()).isEmpty(),
                            r.body().toString());
                } else if (r.type().equals("error")) {
                    assertTrue(rig.schemas.violations("tunnel/error.schema.json", r.body()).isEmpty());
                }
                return r;
            }
        }
    }

    /** Sends a request body (valid or deliberately invalid); returns its frame id. */
    private String request(Live live, ObjectNode body) throws Exception {
        String id = CloudSide.id();
        live.s().sendText(cloud.seal("request", id, null, live.sessionId(), body, Instant.now()));
        return id;
    }

    private static void await(BooleanSupplier condition, int seconds) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "condition not met within " + seconds + " s");
    }

    // ── tests ────────────────────────────────────────────────────────────────────────────────────

    @Test
    void handshakeHeartbeatAndAnAuditedSearchInteroperate() throws Exception {
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ids.add(rig.row().at(t.minusSeconds(i)).decision("BLOCK", 50).insert());
        }
        client.start();
        Live live = handshake();
        assertEquals("CN=" + TunnelRig.EDGE_ID + ",O=PSP", live.s().peerSubject, "mTLS client certificate presented");
        assertEquals(TunnelRig.EDGE_ID, live.s().headers.get("x-hokeka-edge-id"));
        assertEquals("2.4.0", live.s().headers.get("x-edge-agent-version"));
        assertTrue(live.s().headers.get("sec-websocket-protocol").contains("hokeka-tunnel.v1"));
        ObjectNode hello = live.hello().body();
        assertEquals(TunnelRig.SITE_ID, hello.get("siteId").asText());
        assertEquals(12, hello.get("pspId").asInt());
        assertEquals(14, hello.get("configVersion").asInt());
        assertEquals(TunnelOps.IMPLEMENTED.size(), hello.get("opsEnabled").size());
        assertEquals(1, bundlePolls.get(), "hello.ack named a newer bundle: poll it now");
        assertEquals(0, configPolls.get(), "config already current");

        // The cloud pings: the Edge answers pong with corrId = ping id. The Edge pings on its own.
        String ping = cloud.unsealed("ping", null, Instant.now());
        String pingId = rig.mapper.readTree(ping).get("id").asText();
        live.s().sendText(ping);
        boolean pong = false;
        boolean edgePing = false;
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while ((!pong || !edgePing) && System.nanoTime() < end) {
            String text = live.s().next(1);
            if (text != null) {
                CloudSide.Received r = cloud.open(text, live.sessionId());
                pong |= r.type().equals("pong") && pingId.equals(r.corrId());
                edgePing |= r.type().equals("ping");
            }
        }
        assertTrue(pong, "pong answers the cloud ping");
        assertTrue(edgePing, "the Edge pings every heartbeatIntervalMs");

        ObjectNode params = rig.mapper.createObjectNode();
        params.put("from", TunnelTime.millis(t.minus(Duration.ofDays(1))));
        params.put("to", TunnelTime.millis(t.plusSeconds(60)));
        params.putArray("decisions").add("BLOCK");
        ObjectNode body = rig.request("txn.search", params, 2, null, 8000, rig.actor());
        String reqId = request(live, body);
        CloudSide.Received resp = next(live);
        assertEquals("response", resp.type());
        assertEquals(reqId, resp.corrId());
        assertEquals(2, resp.body().get("items").size());
        assertTrue(resp.body().get("last").asBoolean());
        assertNotNull(resp.body().get("nextCursor"));
        assertEquals(ids.get(0).toString(), resp.body().get("items").get(0).get("edgeAssessmentId").asText());

        await(() -> auditOutcome(body.get("auditId").asText()) != null, 5);
        assertEquals("OK", auditOutcome(body.get("auditId").asText()));
        assertEquals(2, auditItems(body.get("auditId").asText()));
    }

    @Test
    void refusedRequestsAreAnsweredAuditedAndRepeatedSealFailuresClose4401() throws Exception {
        client.start();
        Live live = handshake();

        ObjectNode unknownOp = rig.fixture("request--unknown-op.invalid.json");
        String id = request(live, unknownOp);
        CloudSide.Received err = next(live);
        assertEquals("error", err.type());
        assertEquals(id, err.corrId());
        assertEquals("OP_NOT_ALLOWED", err.body().get("code").asText());
        await(() -> "OP_NOT_ALLOWED".equals(auditOutcome(unknownOp.get("auditId").asText())), 5);

        ObjectNode unknownParam = rig.fixture("request--unknown-param.invalid.json");
        request(live, unknownParam);
        CloudSide.Received invalid = next(live);
        assertEquals("INVALID_PARAMS", invalid.body().get("code").asText());
        assertEquals("/params/panHash", invalid.body().get("param").asText());

        ObjectNode support = rig.fixture("request--support-access.json");
        request(live, support);
        assertEquals("SUPPORT_ACCESS_DISABLED", next(live).body().get("code").asText());

        // Seal failures: a replayed frame, a frame bound to another session, a forged signature.
        ObjectNode get = rig.request("txn.get", rig.mapper.createObjectNode().put("edgeAssessmentId",
                UUID.randomUUID().toString()), null, null, 5000, rig.actor());
        String frame = cloud.seal("request", CloudSide.id(), null, live.sessionId(), get, Instant.now());
        live.s().sendText(frame);
        assertEquals("NOT_FOUND", next(live).body().get("code").asText());
        live.s().sendText(frame);
        assertEquals("REPLAY_DETECTED", next(live).body().get("code").asText());
        live.s().sendText(cloud.seal("request", CloudSide.id(), null, UUID.randomUUID().toString(), get, Instant.now()));
        assertEquals("BINDING_MISMATCH", next(live).body().get("code").asText());
        ObjectNode forged = (ObjectNode) rig.mapper.readTree(cloud.seal("request", CloudSide.id(), null,
                live.sessionId(), get, Instant.now()));
        byte[] sealed = java.util.Base64.getDecoder().decode(forged.get("sealed").asText());
        sealed[sealed.length - 1] ^= 1;
        forged.put("sealed", java.util.Base64.getEncoder().encodeToString(sealed));
        live.s().sendText(forged.toString());
        assertEquals(EdgeTunnelClient.CLOSE_SEAL, live.s().closeCode.get(10, TimeUnit.SECONDS));

        // ... and the Edge reconnects with backoff.
        Live again = handshake();
        assertFalse(again.sessionId().equals(live.sessionId()));
    }

    @Test
    void exportStreamsUnderCreditAndCancelStopsIt() throws Exception {
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 1100; i++) {
            rig.row().at(t.minusMillis(i * 10L)).decision("BLOCK", 90).insert();
        }
        client.start();
        Live live = handshake();
        ObjectNode params = rig.mapper.createObjectNode();
        ObjectNode filter = params.putObject("filter");
        filter.put("from", TunnelTime.millis(t.minus(Duration.ofDays(1))));
        filter.put("to", TunnelTime.millis(t.plusSeconds(1)));
        params.put("initialCredit", 1);
        String id = request(live, rig.request("export.stream", params, null, null, 300000,
                rig.actor().put("purpose", "DATA_EXPORT")));
        CloudSide.Received c0 = next(live);
        assertEquals("chunk", c0.type());
        assertEquals(id, c0.corrId());
        assertEquals(0, c0.body().get("seq").asInt());
        assertEquals(500, c0.body().get("items").size());
        assertNull(quiet(live, 1), "no chunk without credit");
        live.s().sendText(cloud.seal("credit", CloudSide.id(), id, live.sessionId(),
                rig.mapper.createObjectNode().put("grant", 5), Instant.now()));
        CloudSide.Received c1 = next(live);
        CloudSide.Received c2 = next(live);
        assertEquals(500, c1.body().get("items").size());
        assertEquals(100, c2.body().get("items").size());
        assertTrue(c2.body().get("last").asBoolean());
        assertEquals(1100, c2.body().get("totalRows").asInt());

        // Cancel: the Edge stops, answers CANCELLED once and sends nothing more for the request.
        String id2 = request(live, rig.request("export.stream", params, null, null, 300000,
                rig.actor().put("purpose", "DATA_EXPORT")));
        assertEquals("chunk", next(live).type());
        live.s().sendText(cloud.seal("cancel", CloudSide.id(), id2, live.sessionId(),
                rig.fixture("cancel--client-gone.json"), Instant.now()));
        CloudSide.Received cancelled = next(live);
        assertEquals("error", cancelled.type());
        assertEquals("CANCELLED", cancelled.body().get("code").asText());
        live.s().sendText(cloud.seal("credit", CloudSide.id(), id2, live.sessionId(),
                rig.fixture("credit--grant.json"), Instant.now()));
        assertNull(quiet(live, 1), "credit after cancel is ignored");

        // No credit for the stall timeout: STREAM_STALLED.
        String id3 = request(live, rig.request("export.stream", params, null, null, 300000,
                rig.actor().put("purpose", "DATA_EXPORT")));
        assertEquals("chunk", next(live).type());
        CloudSide.Received stalled = next(live);
        assertEquals(id3, stalled.corrId());
        assertEquals("STREAM_STALLED", stalled.body().get("code").asText());
    }

    @Test
    void inflightCapAndSupportRevocationAreEnforced() throws Exception {
        rig.acceptConfig(15, d -> {
            TunnelRig.allowAllOps(d);
            ((ObjectNode) d.get("supportAccess")).put("enabled", true);
        });
        for (int i = 0; i < 600; i++) {
            rig.row().decision("BLOCK", 90).insert();
        }
        props.setCreditStallTimeout(Duration.ofSeconds(20));
        ackMaxInflight = 1;
        client.start();
        Live live = handshake();
        ObjectNode params = rig.mapper.createObjectNode();
        ObjectNode filter = params.putObject("filter");
        filter.put("from", TunnelTime.millis(Instant.now().minus(Duration.ofDays(1))));
        filter.put("to", TunnelTime.millis(Instant.now().plusSeconds(60)));
        params.put("initialCredit", 1);
        ObjectNode platform = rig.mapper.createObjectNode().put("kind", "PLATFORM_USER").put("userId", "u-1")
                .put("role", "PLATFORM_ADMIN").put("pspId", 12).put("purpose", "SUPPORT_ACCESS")
                .put("supportReason", "Ticket HK-2291 export check");
        String exportId = request(live, rig.request("export.stream", params, null, null, 300000, platform));
        assertEquals("chunk", next(live).type());

        String busyId = request(live, rig.request("ledger.health", rig.mapper.createObjectNode(), null, null, 3000,
                rig.actor()));
        CloudSide.Received busy = next(live);
        assertEquals(busyId, busy.corrId());
        assertEquals("EDGE_BUSY", busy.body().get("code").asText());
        assertTrue(busy.body().get("retryAfterMs").asLong() >= 0);

        // The PSP withdraws support access: the in-flight platform pull ends on the Edge too (§9).
        rig.acceptConfig(16, TunnelRig::allowAllOps);
        CloudSide.Received revoked = next(live);
        assertEquals(exportId, revoked.corrId());
        assertEquals("SUPPORT_ACCESS_DISABLED", revoked.body().get("code").asText());
    }

    @Test
    void holdNotifiesAreAcknowledgedAndConfigNotifiesTriggerAPoll() throws Exception {
        UUID id = UUID.fromString("3f0c9a2e-7d41-4b8e-9a51-0c2f6b1d7e44");
        rig.row().id(id).insert();
        client.start();
        Live live = handshake();
        String notifyId = CloudSide.id();
        live.s().sendText(cloud.seal("notify", notifyId, null, live.sessionId(), rig.fixture("notify--hold-place.json"),
                Instant.now()));
        CloudSide.Received ack = next(live);
        assertEquals("response", ack.type());
        assertEquals(notifyId, ack.corrId());
        assertEquals("hold.ack", ack.body().get("op").asText());
        ObjectNode item = (ObjectNode) ack.body().get("items").get(0);
        assertEquals("APPLIED", item.get("state").asText());
        assertEquals(1, item.get("matchedRows").asInt());
        assertEquals(rig.fixture("response--hold-ack.json").get("items").get(0).get("activeHoldDigest").asText(),
                item.get("activeHoldDigest").asText());

        String releaseId = CloudSide.id();
        live.s().sendText(cloud.seal("notify", releaseId, null, live.sessionId(),
                rig.fixture("notify--hold-release.json"), Instant.now()));
        assertEquals("RELEASED", next(live).body().get("items").get(0).get("state").asText());

        live.s().sendText(cloud.seal("notify", CloudSide.id(), null, live.sessionId(),
                rig.fixture("notify--config-updated.json"), Instant.now()));
        await(() -> configPolls.get() == 1, 5);
        ObjectNode unknown = rig.mapper.createObjectNode().put("type", "future.thing");
        live.s().sendText(cloud.seal("notify", CloudSide.id(), null, live.sessionId(), unknown, Instant.now()));
        assertNull(quiet(live, 1), "an unknown notify type is not acknowledged");
    }

    @Test
    void closeCodesDriveTheReconnectRules() throws Exception {
        client.start();
        Live live = handshake();
        live.s().sendClose(EdgeTunnelClient.CLOSE_GOING_AWAY, "deploy");
        Live second = handshake();
        assertEquals(EdgeTunnelClient.State.OPEN, client.state());

        // 4409: a newer session of this node is live — no reconnect during the hold-off.
        int upgrades = server.upgrades.get();
        second.s().sendClose(EdgeTunnelClient.CLOSE_REPLACED, "replaced");
        await(() -> client.state() == EdgeTunnelClient.State.SUPERSEDED, 5);
        Thread.sleep(1000);
        assertEquals(upgrades, server.upgrades.get(), "no fight-reconnect after 4409");
        Live third = handshake();

        // 4403: no reconnect until the next successful bundle poll.
        upgrades = server.upgrades.get();
        third.s().sendClose(EdgeTunnelClient.CLOSE_FORBIDDEN, "not authorised");
        await(() -> client.state() == EdgeTunnelClient.State.FORBIDDEN, 5);
        Thread.sleep(800);
        assertEquals(upgrades, server.upgrades.get());
        bundleOk.set(Instant.now().plusSeconds(1));
        Live fourth = handshake();

        // A binary frame from the cloud: the Edge closes 4400 and reconnects.
        fourth.s().sendBinary(new byte[]{1, 2, 3});
        assertEquals(EdgeTunnelClient.CLOSE_MALFORMED, fourth.s().closeCode.get(10, TimeUnit.SECONDS));
        Live fifth = handshake();

        // A frame over 1 MiB: 4413.
        fifth.s().sendText("{\"pad\":\"" + "x".repeat(TunnelFrameCodec.MAX_FRAME_BYTES) + "\"}");
        assertEquals(EdgeTunnelClient.CLOSE_TOO_LARGE, fifth.s().closeCode.get(10, TimeUnit.SECONDS));
        Live sixth = handshake();

        // 4426: no common version — the Edge stays down.
        upgrades = server.upgrades.get();
        sixth.s().sendClose(EdgeTunnelClient.CLOSE_VERSION, "upgrade required");
        await(() -> client.state() == EdgeTunnelClient.State.VERSION_UNSUPPORTED, 5);
        Thread.sleep(800);
        assertEquals(upgrades, server.upgrades.get());
        assertEquals(4426, client.status().get("lastCloseCode"));
    }

    @Test
    void aMissingSubprotocolIsRefusedAndNoHelloAckMeansClose4408() throws Exception {
        server.skipSubprotocolOnUpgrade = 1;
        client.start();
        FakeTunnelServer.Session noProto = server.sessions.poll(15, TimeUnit.SECONDS);
        assertNotNull(noProto);
        assertEquals(EdgeTunnelClient.CLOSE_MALFORMED, noProto.closeCode.get(10, TimeUnit.SECONDS));
        assertTrue(noProto.texts.isEmpty(), "no hello without the subprotocol");

        FakeTunnelServer.Session s = server.sessions.poll(15, TimeUnit.SECONDS);
        assertNotNull(s);
        assertEquals("hello", cloud.open(s.next(10), null).type());
        assertEquals(EdgeTunnelClient.CLOSE_TIMEOUT, s.closeCode.get(10, TimeUnit.SECONDS), "no hello.ack in time");
        handshake();
    }

    /** The next sealed frame within {@code seconds}, skipping heartbeats; null when none arrives. */
    private CloudSide.Received quiet(Live live, int seconds) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            String text = live.s().texts.poll(50, TimeUnit.MILLISECONDS);
            if (text != null) {
                CloudSide.Received r = cloud.open(text, live.sessionId());
                if (!r.type().equals("ping") && !r.type().equals("pong")) {
                    return r;
                }
            }
        }
        return null;
    }

    private String auditOutcome(String auditId) {
        return auditColumn(auditId, "outcome");
    }

    private int auditItems(String auditId) {
        String v = auditColumn(auditId, "item_count::text");
        return v == null ? -1 : Integer.parseInt(v);
    }

    private String auditColumn(String auditId, String column) {
        try (Connection c = LedgerTestDatabase.connect(rig.schema);
             PreparedStatement ps = c.prepareStatement("SELECT " + column
                     + " FROM edge_access_log WHERE audit_id = ?::uuid ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, auditId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
