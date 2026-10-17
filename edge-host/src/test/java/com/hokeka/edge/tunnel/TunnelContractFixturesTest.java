package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.channel.ReplayGuard;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.contract.Jcs;
import com.hokeka.edge.residency.EdgeConfigDocument;
import com.hokeka.edge.residency.LegalHoldService;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every tunnel and Edge-config fixture of {@code docs/contracts/tunnel/examples} (contract §13), each
 * pushed through the Edge code path that consumes it — not just a schema check: outer frames through
 * {@link TunnelFrameCodec#parseOuter}, sealed plaintexts through a real HSE-1 seal and
 * {@link TunnelFrameCodec#open} (binding), requests and op params through {@link TunnelOps#prepare}
 * (expected §7.11 code for each invalid one), items through the outgoing item check, cursors through
 * {@link TunnelCursor}, errors through {@link TunnelException#toBody}, notifies through the hold
 * parser, configs through {@link EdgeConfigDocument#parse}. Decision-record and metrics fixtures
 * belong to R1's {@code ContractFixturesTest}.
 */
class TunnelContractFixturesTest {

    private static final Path EXAMPLES = TunnelRig.CONTRACTS.resolve("tunnel/examples");

    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractSchemas schemas = ContractSchemas.fromDirectory(TunnelRig.CONTRACTS);
    private final TunnelFrameCodec codec = new TunnelFrameCodec(mapper, new ReplayGuard(), schemas, TunnelRig.EDGE_ID);
    private final TunnelCursor cursors = new TunnelCursor(mapper, schemas);
    private final TunnelOps ops = new TunnelOps(codec, schemas, null, new TunnelItems(mapper), cursors, null, null, null,
            null, null, mapper, Clock.systemUTC());
    private final KeyPair edgeX = CloudSide.generate("X25519");
    private final KeyPair edgeEd = CloudSide.generate("Ed25519");
    private final CloudSide cloud = new CloudSide(TunnelRig.EDGE_ID, edgeX.getPublic(), edgeEd.getPublic());

    /** Expected refusal of each invalid request fixture: code and param pointer. */
    private static final Map<String, String[]> REQUEST_REFUSALS = Map.of(
            "request--backfill-as-psp-user.invalid.json", new String[]{"INVALID_PARAMS", "/actor/kind"},
            "request--interactive-deadline-too-long.invalid.json", new String[]{"INVALID_PARAMS", "/deadlineMs"},
            "request--page-limit-exceeded.invalid.json", new String[]{"PAGE_LIMIT_EXCEEDED", "/page/limit"},
            "request--platform-without-reason.invalid.json", new String[]{"INVALID_PARAMS", "/actor/supportReason"},
            "request--unknown-op.invalid.json", new String[]{"OP_NOT_ALLOWED", null},
            "request--unknown-param.invalid.json", new String[]{"INVALID_PARAMS", "/params/panHash"});

    /** One dynamic test per fixture, dispatched by the fixture's schema stem. */
    @TestFactory
    Stream<DynamicTest> everyTunnelAndConfigFixtureGoesThroughItsEdgeConsumer() throws Exception {
        List<Path> fixtures;
        try (Stream<Path> files = Files.list(EXAMPLES)) {
            fixtures = files.map(Path::getFileName).map(Path::toString)
                    .filter(n -> n.endsWith(".json") && !n.equals("expected-failures.json"))
                    .filter(n -> !n.startsWith("decision-record") && !n.startsWith("metrics-report"))
                    .sorted().map(EXAMPLES::resolve).toList();
        }
        assertTrue(fixtures.size() >= 70, "the full tunnel/config fixture set, found " + fixtures.size());
        return fixtures.stream().map(f -> DynamicTest.dynamicTest(f.getFileName().toString(), () -> consume(f)));
    }

    /** Routes one fixture to its consumer. */
    private void consume(Path file) throws Exception {
        String name = file.getFileName().toString();
        String stem = name.substring(0, name.indexOf("--"));
        boolean valid = !name.endsWith(".invalid.json");
        ObjectNode fx = (ObjectNode) mapper.readTree(Files.readString(file));
        switch (stem) {
            case "frame" -> frame(fx, valid);
            case "sealed-plaintext" -> sealedPlaintext(fx, valid);
            case "request" -> request(name, fx, valid);
            case "response" -> response(name, fx, valid);
            case "error" -> error(fx, valid);
            case "notify" -> notifyBody(fx, valid);
            case "hello", "hello-ack", "cancel", "credit" -> body(stem.equals("hello-ack") ? "hello.ack" : stem, fx, valid);
            case "cursor" -> cursor(fx);
            case "composite-cursor" -> schemaOnly("tunnel/composite-cursor.schema.json", fx, valid); // cloud-only
            case "edge-config" -> config(fx, valid);
            case "hold.ack.item" -> schemaOnly("tunnel/notify/hold.ack.item.schema.json", fx, valid);
            case "sanctions-list-manifest", "sanctions-list-chunk", "sanctions-list-entry" -> sanctionsList(stem, fx, valid);
            default -> {
                if (stem.endsWith(".params")) {
                    opParams(stem.substring(0, stem.length() - ".params".length()), name, fx, valid);
                } else if (stem.endsWith(".item")) {
                    opItem(stem.substring(0, stem.length() - ".item".length()), fx, valid);
                } else {
                    fail("fixture " + name + " has no Edge consumer in this test");
                }
            }
        }
    }

    /** Outer frames: parseOuter accepts the valid ones and refuses the invalid ones as malformed (4400). */
    private void frame(ObjectNode fx, boolean valid) throws Exception {
        if (valid) {
            assertEquals(fx, codec.parseOuter(fx.toString()));
        } else {
            assertEquals(TunnelErrorCode.INVALID_PARAMS,
                    assertThrows(TunnelException.class, () -> codec.parseOuter(fx.toString())).code());
        }
    }

    /**
     * Sealed plaintexts: a cloud→Edge plaintext is sealed with the independent HSE-1 under its
     * frame-type context and opened by the Edge (the invalid one fails the session binding); the
     * hello plaintext (Edge→cloud) is reproduced by the Edge's own seal and opened by the cloud.
     */
    private void sealedPlaintext(ObjectNode fx, boolean valid) throws Exception {
        // Fixtures share nonces, so each gets a fresh replay window.
        TunnelFrameCodec codec = new TunnelFrameCodec(mapper, new ReplayGuard(), schemas, TunnelRig.EDGE_ID);
        assertEquals(valid, schemas.violations("tunnel/sealed-plaintext.schema.json", fx).isEmpty());
        JsonNode frame = fx.get("payload").get("frame");
        String type = frame.get("type").asText();
        Instant issued = Instant.parse(fx.get("issuedAt").asText());
        if (type.equals("hello")) {
            byte[] cpRaw = CloudSide.raw((java.security.interfaces.XECPublicKey) cloud.cpX25519.getPublic());
            String text = codec.seal("hello", frame.get("id").asText(), null, null, fx.get("payload").get("body"), cpRaw,
                    edgeEd.getPrivate(), Instant.now());
            CloudSide.Received r = cloud.open(text, null);
            assertEquals(fx.get("payload").get("body"), r.body());
            return;
        }
        byte[] sealed = CloudSide.hseSeal(fx.toString().getBytes(StandardCharsets.UTF_8),
                CloudSide.raw((java.security.interfaces.XECPublicKey) edgeX.getPublic()), cloud.cpEd25519.getPrivate(),
                ("hokeka.tunnel." + type).getBytes(StandardCharsets.UTF_8));
        ObjectNode outer = mapper.createObjectNode();
        outer.put("v", 1).put("type", type).put("id", frame.get("id").asText());
        if (frame.has("corrId")) {
            outer.put("corrId", frame.get("corrId").asText());
        }
        outer.put("ts", issued.toEpochMilli()).put("nonce", fx.get("nonce").asText())
                .put("sealed", Base64.getEncoder().encodeToString(sealed));
        ObjectNode parsed = codec.parseOuter(outer.toString());
        String session = frame.has("sessionId") ? frame.get("sessionId").asText() : "0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b";
        if (valid) {
            TunnelFrameCodec.Opened o = codec.open(parsed, edgeX.getPrivate(), cloud.cpEd25519.getPublic(), session, issued);
            assertEquals(fx.get("payload").get("body"), o.body());
            assertTrue(codec.checkBody(type, o.body()).isEmpty());
            // The same bytes again are a replay; under another context they do not open at all.
            assertEquals(TunnelErrorCode.REPLAY_DETECTED, assertThrows(TunnelException.class,
                    () -> codec.open(parsed, edgeX.getPrivate(), cloud.cpEd25519.getPublic(), session, issued)).code());
            ObjectNode asCancel = parsed.deepCopy().put("type", "cancel");
            assertEquals(TunnelErrorCode.SEAL_INVALID, assertThrows(TunnelException.class,
                    () -> codec.open(asCancel, edgeX.getPrivate(), cloud.cpEd25519.getPublic(), session, issued)).code());
        } else {
            assertEquals(TunnelErrorCode.BINDING_MISMATCH, assertThrows(TunnelException.class,
                    () -> codec.open(parsed, edgeX.getPrivate(), cloud.cpEd25519.getPublic(), session, issued)).code());
        }
    }

    /** Requests: prepared by the op gate; each invalid one refused with its expected code and pointer. */
    private void request(String name, ObjectNode fx, boolean valid) throws Exception {
        TunnelFrameCodec.Opened o = opened(fx);
        if (valid) {
            TunnelOps.Prepared p = ops.prepare(o, permissiveGate());
            assertEquals(fx.get("op").asText(), p.op());
            if (p.op().equals("ledger.backfill")) {
                assertTrue(p.backfillRows().stream().allMatch(BackfillImporter.BatchRow::schemaValid));
            }
            assertFalse(ops.prepare(opened(fx), permissiveGate()).control().cancelled());
            if (fx.get("actor").get("kind").asText().equals("PLATFORM_USER")) {
                TunnelOps.Gate noConsent = new TunnelOps.Gate(TunnelRig.PSP_ID, Optional.of(config(false, false)),
                        new LinkedHashSet<>(TunnelOps.IMPLEMENTED));
                assertEquals(TunnelErrorCode.SUPPORT_ACCESS_DISABLED,
                        assertThrows(TunnelException.class, () -> ops.prepare(opened(fx), noConsent)).code());
            }
            return;
        }
        String[] expected = REQUEST_REFUSALS.get(name);
        assertTrue(expected != null, "expected refusal recorded for " + name);
        TunnelException e = assertThrows(TunnelException.class, () -> ops.prepare(o, permissiveGate()));
        assertEquals(expected[0], e.code().name(), name);
        assertEquals(expected[1], e.param(), name);
        assertTrue(schemas.violations("tunnel/error.schema.json", e.toBody(mapper)).isEmpty());
    }

    /** Op params: wrapped in a request for their op and prepared. */
    private void opParams(String op, String name, ObjectNode fx, boolean valid) throws Exception {
        ObjectNode actor = mapper.createObjectNode().put("kind", "PSP_USER").put("userId", "u-4512")
                .put("role", "COMPLIANCE_OFFICER").put("pspId", 12).put("purpose", "CASE_INVESTIGATION");
        long deadline = 8000;
        if (op.equals("ledger.backfill")) {
            actor = mapper.createObjectNode().put("kind", "SYSTEM").put("userId", "system:backfill").put("role", "SYSTEM")
                    .put("pspId", 12).put("purpose", "LEDGER_BACKFILL");
            deadline = 60000;
        } else if (op.equals("export.stream")) {
            actor.put("purpose", "DATA_EXPORT");
            deadline = 300000;
        } else if (op.equals("filing.extract")) {
            deadline = 15000;
        }
        ObjectNode body = mapper.createObjectNode().put("op", op);
        body.set("params", fx);
        body.put("deadlineMs", deadline);
        if (op.equals("txn.search") || op.equals("txn.related")) {
            body.putObject("page").put("limit", 50);
        }
        body.set("actor", actor);
        body.put("auditId", UUID.randomUUID().toString());
        if (name.equals("ledger.backfill.params--raw-pan-as-hash.invalid.json")) {
            // A bad row does not fail the batch: it is rejected as SCHEMA_INVALID on import (§11 step 4).
            TunnelOps.Prepared p = ops.prepare(opened(body), permissiveGate());
            assertFalse(p.backfillRows().get(0).schemaValid());
            return;
        }
        if (valid) {
            assertEquals(op, ops.prepare(opened(body), permissiveGate()).op());
        } else {
            TunnelException e = assertThrows(TunnelException.class, () -> ops.prepare(opened(body), permissiveGate()));
            assertEquals(name.contains("too-many") ? TunnelErrorCode.PAGE_LIMIT_EXCEEDED : TunnelErrorCode.INVALID_PARAMS,
                    e.code(), name);
        }
    }

    /** Items: kept or dropped by the outgoing item check. */
    private void opItem(String op, ObjectNode fx, boolean valid) {
        assertEquals(valid ? 1 : 0, ops.checked(op, List.of(fx)).size());
    }

    /** Responses: the Edge's response-body check; the search page's cursor matches the cursor fixture. */
    private void response(String name, ObjectNode fx, boolean valid) throws Exception {
        assertEquals(valid, codec.checkBody("response", fx).isEmpty(), name);
        if (name.equals("response--search-page.json")) {
            ObjectNode req = (ObjectNode) mapper.readTree(Files.readString(EXAMPLES.resolve("request--txn-search.json")));
            ObjectNode site = (ObjectNode) mapper.readTree(Files.readString(EXAMPLES.resolve("cursor--site.json")));
            String q = TunnelCursor.queryHash(req.get("params"));
            assertEquals(site.get("q").asText(), q, "JCS q hash matches the fixture");
            TunnelCursor.Key k = cursors.decode(fx.get("nextCursor").asText(), q);
            assertEquals(site.get("k").get(1).asText(), k.id().toString());
            assertEquals(fx.get("nextCursor").asText(), cursors.encode(q, k.evaluatedAt(), k.id()),
                    "the Edge mints byte-identical cursors");
        }
        if (name.equals("response--hold-ack.json")) {
            ObjectNode built = new ResponseFrames(mapper).body("hold.ack", 0, true,
                    List.of((ObjectNode) fx.get("items").get(0)), null, null, null, null);
            assertEquals(fx, built);
            String holdId = fx.get("items").get(0).get("holdId").asText();
            assertEquals(Jcs.sha256Hex(holdId.getBytes(StandardCharsets.UTF_8)),
                    fx.get("items").get(0).get("activeHoldDigest").asText(), "digest = sha256 of sorted ids");
        }
    }

    /** Errors: the Edge renders the same body for the codes it raises. */
    private void error(ObjectNode fx, boolean valid) {
        assertEquals(valid, codec.checkBody("error", fx).isEmpty());
        if (!valid) {
            return;
        }
        TunnelErrorCode code = TunnelErrorCode.valueOf(fx.get("code").asText());
        if (code == TunnelErrorCode.EDGE_OFFLINE) {
            return; // raised only by the Control Plane
        }
        TunnelException e = new TunnelException(code, fx.get("message").asText(), fx.path("param").asText(null),
                fx.has("retryAfterMs") ? fx.get("retryAfterMs").asLong() : null);
        ObjectNode body = e.toBody(mapper);
        if (fx.has("siteId")) {
            body.put("siteId", fx.get("siteId").asText());
        }
        try {
            assertEquals(fx, mapper.readTree(body.toString()), "same JSON as the fixture");
        } catch (java.io.IOException io) {
            throw new IllegalStateException(io);
        }
    }

    /** Notifies: the notify check, and hold.place parses into a placement with exactly one scope. */
    private void notifyBody(ObjectNode fx, boolean valid) {
        assertEquals(valid, codec.checkBody("notify", fx).isEmpty());
        if (valid && fx.get("type").asText().equals("hold.place")) {
            LegalHoldService.Placement p = EdgeTunnelClient.placement(fx);
            assertEquals(fx.get("scope").has("edgeAssessmentIds"), p.byIds());
        }
    }

    /** A body checked against its frame type's schema. */
    private void body(String type, ObjectNode fx, boolean valid) {
        assertEquals(valid, codec.checkBody(type, fx).isEmpty());
    }

    /** The site cursor fixture round-trips through the Edge cursor codec. */
    private void cursor(ObjectNode fx) throws Exception {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(fx.toString().getBytes(StandardCharsets.UTF_8));
        TunnelCursor.Key k = cursors.decode(token, fx.get("q").asText());
        assertEquals(Instant.parse(fx.get("k").get(0).asText()), k.evaluatedAt());
        assertEquals(TunnelErrorCode.INVALID_CURSOR, assertThrows(TunnelException.class,
                () -> cursors.decode(token, "0".repeat(64))).code());
    }

    /** Config fixtures: schema verdict, and the valid ones parse into the values the Edge consumes. */
    private void config(ObjectNode fx, boolean valid) {
        assertEquals(valid, schemas.violations("edge-config.schema.json", fx).isEmpty());
        if (valid) {
            EdgeConfigDocument d = EdgeConfigDocument.parse(fx, Jcs.sha256Hex(fx));
            assertEquals(fx.get("retention").get("years").asInt(), d.retentionYears());
            assertEquals(fx.get("tunnel").get("maxInflightPerSession").asInt(), d.tunnel().maxInflightPerSession());
        }
    }

    /** Schema check for documents only the Control Plane interprets. */
    private void schemaOnly(String schema, ObjectNode fx, boolean valid) {
        assertEquals(valid, schemas.violations(schema, fx).isEmpty());
    }

    /**
     * R10b sanctions list documents through the Edge's own checks: the schema, then the manifest's
     * listHash/entryCount consistency, the chunk's gzip + SHA-256 + entry parsing, and the entry parser
     * (which refuses the invalid entries for the same reasons as the schema).
     */
    private void sanctionsList(String stem, ObjectNode fx, boolean valid) throws Exception {
        assertEquals(valid, schemas.violations(stem + ".schema.json", fx).isEmpty(), stem);
        if (!valid) {
            if (stem.equals("sanctions-list-entry")) {
                assertThrows(com.hokeka.edge.sanctions.SanctionsListCodec.InvalidListException.class,
                        () -> com.hokeka.edge.sanctions.SanctionsListCodec.entry(fx.toString(), mapper));
            }
            return;
        }
        switch (stem) {
            case "sanctions-list-manifest" -> com.hokeka.edge.sanctions.SanctionsListCodec.verifyManifest(fx);
            case "sanctions-list-chunk" -> {
                byte[] raw = com.hokeka.edge.sanctions.SanctionsListCodec.gunzip(
                        Base64.getDecoder().decode(fx.get("data").asText()));
                assertEquals(fx.get("entryCount").asInt(), com.hokeka.edge.sanctions.SanctionsListCodec.parseChunk(
                        raw, fx.get("sha256").asText(), fx.get("entryCount").asInt(), mapper).size());
            }
            default -> assertEquals(fx.get("id").asText(),
                    com.hokeka.edge.sanctions.SanctionsListCodec.entry(fx.toString(), mapper).id());
        }
    }

    /** An opened request issued now. */
    private TunnelFrameCodec.Opened opened(ObjectNode body) {
        return new TunnelFrameCodec.Opened("request", UUID.randomUUID().toString(), null, UUID.randomUUID().toString(),
                ReplayGuard.newNonce(), Instant.now(), body);
    }

    /** All ops effective, support access and backfill allowed. */
    private TunnelOps.Gate permissiveGate() throws Exception {
        return new TunnelOps.Gate(TunnelRig.PSP_ID, Optional.of(config(true, true)), new LinkedHashSet<>(TunnelOps.IMPLEMENTED));
    }

    /** The default config fixture with support access / backfill set. */
    private EdgeConfigDocument config(boolean support, boolean backfill) throws Exception {
        ObjectNode doc = (ObjectNode) mapper.readTree(Files.readString(EXAMPLES.resolve("edge-config--default.json")));
        ((ObjectNode) doc.get("supportAccess")).put("enabled", support);
        ((ObjectNode) doc.get("tunnel")).put("backfillAllowed", backfill);
        ArrayNode opsAllowed = ((ObjectNode) doc.get("tunnel")).putArray("opsAllowed");
        TunnelOps.IMPLEMENTED.forEach(opsAllowed::add);
        return EdgeConfigDocument.parse(doc, Jcs.sha256Hex(doc));
    }

    // ── codec unit checks beyond the fixtures ────────────────────────────────────────────────────

    @Test
    void sealedFramesRespectTheOneMebibyteCap() {
        byte[] cpRaw = CloudSide.raw((java.security.interfaces.XECPublicKey) cloud.cpX25519.getPublic());
        ObjectNode big = mapper.createObjectNode().put("op", "txn.search").put("status", "OK").put("seq", 0)
                .put("last", true);
        big.putArray("items").add(mapper.createObjectNode().put("pad", "x".repeat(800_000)));
        assertEquals(TunnelErrorCode.FRAME_TOO_LARGE, assertThrows(TunnelException.class,
                () -> codec.seal("response", TunnelFrameCodec.newId(), TunnelFrameCodec.newId(), UUID.randomUUID().toString(),
                        big, cpRaw, edgeEd.getPrivate(), Instant.now())).code());
    }

    @Test
    void framesAddressedToAnotherNodeOrOutsideTheSkewWindowAreRefused() throws Exception {
        String session = UUID.randomUUID().toString();
        ObjectNode body = mapper.createObjectNode().put("reason", "DEADLINE");
        String corr = TunnelFrameCodec.newId();
        String other = cloud.seal("cancel", CloudSide.id(), corr, session, "acme-eu-2", body, Instant.now(), null);
        assertEquals(TunnelErrorCode.BINDING_MISMATCH, assertThrows(TunnelException.class,
                () -> codec.open(codec.parseOuter(other), edgeX.getPrivate(), cloud.cpEd25519.getPublic(), session,
                        Instant.now())).code());
        String stale = cloud.seal("cancel", CloudSide.id(), corr, session, body, Instant.now().minusSeconds(301));
        assertEquals(TunnelErrorCode.REPLAY_DETECTED, assertThrows(TunnelException.class,
                () -> codec.open(codec.parseOuter(stale), edgeX.getPrivate(), cloud.cpEd25519.getPublic(), session,
                        Instant.now())).code());
        String corrMismatch = cloud.seal("cancel", CloudSide.id(), corr, session, TunnelRig.EDGE_ID, body, Instant.now(),
                p -> ((ObjectNode) p.get("payload").get("frame")).put("corrId", TunnelFrameCodec.newId()));
        assertEquals(TunnelErrorCode.BINDING_MISMATCH, assertThrows(TunnelException.class,
                () -> codec.open(codec.parseOuter(corrMismatch), edgeX.getPrivate(), cloud.cpEd25519.getPublic(), session,
                        Instant.now())).code());
        KeyPair impostor = CloudSide.generate("Ed25519");
        String ok = cloud.seal("cancel", CloudSide.id(), corr, session, body, Instant.now());
        assertEquals(TunnelErrorCode.SEAL_INVALID, assertThrows(TunnelException.class,
                () -> codec.open(codec.parseOuter(ok), edgeX.getPrivate(), impostor.getPublic(), session,
                        Instant.now())).code(), "verified against the pinned Control Plane key only");
    }

    @Test
    void creditOverflowAndJcsNumbersFollowTheContract() throws Exception {
        EdgeTunnelClient.Credit credit = new EdgeTunnelClient.Credit(4, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertTrue(credit.grant(64));
        for (int i = 0; i < 14; i++) {
            assertTrue(credit.grant(64));
        }
        assertFalse(credit.grant(64), "more than 1 024 outstanding chunks is CREDIT_VIOLATION");
        assertEquals("{\"a\":1,\"b\":[1e+21,0.5,\"\\u0001\"]}",
                Jcs.canonicalize(mapper.readTree("{\"b\":[1e21,0.50,\"\\u0001\"],\"a\":1.0}")));
    }
}
