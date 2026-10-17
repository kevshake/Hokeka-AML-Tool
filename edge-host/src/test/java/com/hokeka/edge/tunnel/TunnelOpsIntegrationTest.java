package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.contract.Jcs;
import com.hokeka.edge.ledger.LedgerTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every tunnel op handler against a real PostgreSQL Edge Ledger (contract §7.5, §7.6, §11): results,
 * ordering, keyset cursors ({@code q} hash), limits (93-day search, 366-day / maxRows export),
 * gates, and that every item passes its item schema and never carries {@code pan_hash}.
 */
class TunnelOpsIntegrationTest {

    private TunnelRig rig;

    @BeforeEach
    void setUp() {
        rig = new TunnelRig();
        rig.acceptConfig(14, null);
    }

    @AfterEach
    void tearDown() {
        rig.close();
    }

    private ObjectNode searchParams(Instant from, Instant to) {
        ObjectNode p = rig.mapper.createObjectNode();
        p.put("from", TunnelTime.millis(from));
        p.put("to", TunnelTime.millis(to));
        p.put("merchantId", "mrc_00421");
        p.putArray("decisions").add("ALERT").add("HOLD").add("BLOCK");
        return p;
    }

    @Test
    void searchPagesWithKeysetCursorsInContractOrder() throws Exception {
        Instant base = rig.clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Set<UUID> expected = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            expected.add(rig.row().at(base.minusSeconds(60L * i)).decision(i % 2 == 0 ? "ALERT" : "BLOCK", 30, 1204L)
                    .card("sha256:card-a").insert());
        }
        // Same instant, two ids: ordering falls back to the id (desc).
        expected.add(rig.row().at(base.minusSeconds(60)).decision("HOLD", 10).insert());
        rig.row().at(base.minusSeconds(30)).decision("ALLOW", 0).insert();
        rig.row().at(base.minusSeconds(30)).merchant("mrc_other").decision("BLOCK", 99).insert();
        rig.row().at(base.minus(Duration.ofDays(20))).decision("BLOCK", 99).insert();

        ObjectNode params = searchParams(base.minus(Duration.ofDays(10)), base.plusSeconds(3600));
        List<ObjectNode> all = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            TunnelOps.Result r = rig.run(rig.request("txn.search", params, 2, cursor, 8000, rig.actor()));
            assertTrue(r.items().size() <= 2);
            all.addAll(r.items());
            cursor = r.nextCursor();
            pages++;
        } while (cursor != null);
        assertEquals(3, pages);
        assertEquals(6, all.size());
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            ObjectNode item = all.get(i);
            assertTrue(seen.add(UUID.fromString(item.get("edgeAssessmentId").asText())), "no duplicates across pages");
            assertFalse(item.has("pan_hash") || item.has("panHash") || item.has("features") || item.has("reasons"));
            assertTrue(rig.schemas.violations("tunnel/ops/txn.search.item.schema.json", item).isEmpty());
            if (i > 0) {
                ObjectNode prev = all.get(i - 1);
                int c = prev.get("evaluatedAt").asText().compareTo(item.get("evaluatedAt").asText());
                assertTrue(c > 0 || (c == 0 && prev.get("edgeAssessmentId").asText()
                        .compareTo(item.get("edgeAssessmentId").asText()) > 0), "(evaluatedAt desc, id desc)");
            }
        }
        assertEquals(expected, seen);

        // The cursor is the documented keyset: {v, k:[evaluatedAt, id], q = sha256(JCS(params))}.
        TunnelOps.Result first = rig.run(rig.request("txn.search", params, 2, null, 8000, rig.actor()));
        TunnelCursor.Key key = new TunnelCursor(rig.mapper, rig.schemas).decode(first.nextCursor(), Jcs.sha256Hex(params));
        assertEquals(first.items().get(1).get("edgeAssessmentId").asText(), key.id().toString());

        // A cursor minted for different params is INVALID_CURSOR.
        ObjectNode other = params.deepCopy();
        other.put("minScore", 1);
        TunnelException e = assertThrows(TunnelException.class,
                () -> rig.run(rig.request("txn.search", other, 2, first.nextCursor(), 8000, rig.actor())));
        assertEquals(TunnelErrorCode.INVALID_CURSOR, e.code());
        TunnelException garbage = assertThrows(TunnelException.class,
                () -> rig.run(rig.request("txn.search", params, 2, "bm90LWEtY3Vyc29y", 8000, rig.actor())));
        assertEquals(TunnelErrorCode.INVALID_CURSOR, garbage.code());
    }

    @Test
    void searchWindowIsCappedAt93DaysAndFiltersApply() throws Exception {
        Instant now = rig.clock.instant();
        ObjectNode wide = searchParams(now.minus(Duration.ofDays(94)), now);
        TunnelException e = assertThrows(TunnelException.class,
                () -> rig.run(rig.request("txn.search", wide, 50, null, 8000, rig.actor())));
        assertEquals(TunnelErrorCode.INVALID_PARAMS, e.code());
        assertEquals("/params/to", e.param());

        UUID hit = rig.row().ref("AUTH-1").decision("BLOCK", 70, 77L).insert();
        rig.row().ref("AUTH-2").decision("BLOCK", 10, 77L).insert();
        ObjectNode p = rig.mapper.createObjectNode();
        p.put("from", TunnelTime.millis(now.minus(Duration.ofDays(1))));
        p.put("to", TunnelTime.millis(now.plusSeconds(60)));
        p.put("ruleId", 77);
        p.put("minScore", 50);
        p.put("txnRef", "AUTH-1");
        TunnelOps.Result r = rig.run(rig.request("txn.search", p, 50, null, 8000, rig.actor()));
        assertEquals(1, r.items().size());
        assertEquals(hit.toString(), r.items().get(0).get("edgeAssessmentId").asText());
        assertNull(r.nextCursor());
    }

    @Test
    void getAndBatchGetReturnDetailsWithoutCardData() throws Exception {
        UUID a = rig.row().ref("AUTH-2026-10-16-000981").card("sha256:secret-card").feature("device_id", "dv-1")
                .feature("memo", "4111 1111 1111 1111").reasons("Card velocity above 10/h").decision("ALERT", 45, 1204L)
                .insert();
        UUID b = rig.row().insert();
        ObjectNode byId = rig.mapper.createObjectNode().put("edgeAssessmentId", a.toString());
        TunnelOps.Result r = rig.run(rig.request("txn.get", byId, null, null, 5000, rig.actor()));
        ObjectNode item = r.items().get(0);
        assertTrue(rig.schemas.violations("tunnel/ops/txn.get.item.schema.json", item).isEmpty());
        assertEquals("dv-1", item.get("features").get("device_id").asText());
        assertFalse(item.get("features").has("memo"), "a PAN-like feature value is never returned");
        assertFalse(item.toString().contains("secret-card"), "pan_hash never leaves the ledger");
        assertEquals("Card velocity above 10/h", item.get("reasons").get(0).asText());
        assertFalse(item.get("legalHold").asBoolean());

        ObjectNode byRef = rig.mapper.createObjectNode().put("txnRef", "AUTH-2026-10-16-000981");
        assertEquals(a.toString(), rig.run(rig.request("txn.get", byRef, null, null, 5000, rig.actor())).items().get(0)
                .get("edgeAssessmentId").asText());

        ObjectNode absent = rig.mapper.createObjectNode().put("edgeAssessmentId", UUID.randomUUID().toString());
        assertEquals(TunnelErrorCode.NOT_FOUND, assertThrows(TunnelException.class,
                () -> rig.run(rig.request("txn.get", absent, null, null, 5000, rig.actor()))).code());

        UUID missing = UUID.randomUUID();
        ObjectNode ids = rig.mapper.createObjectNode();
        ids.putArray("edgeAssessmentIds").add(b.toString()).add(missing.toString()).add(a.toString());
        TunnelOps.Result batch = rig.run(rig.request("txn.batchGet", ids, null, null, 8000, rig.actor()));
        assertEquals(List.of(b.toString(), a.toString()),
                batch.items().stream().map(n -> n.get("edgeAssessmentId").asText()).toList(), "request order");
        assertEquals(List.of(missing.toString()), batch.missing());
        assertEquals(List.of(b, a), batch.returnedIds());
    }

    @Test
    void relatedFollowsLinksToDepthTwoWithoutKeyValues() throws Exception {
        Instant t = rig.clock.instant().truncatedTo(ChronoUnit.SECONDS);
        UUID anchor = rig.row().at(t).card("sha256:card-1").feature("device_id", "dv-1").insert();
        UUID sameCard = rig.row().at(t.minusSeconds(10)).card("sha256:card-1").feature("device_id", "dv-9").insert();
        UUID sameDevice = rig.row().at(t.minusSeconds(20)).card("sha256:card-2").feature("device_id", "dv-1").insert();
        UUID viaHop1Device = rig.row().at(t.minusSeconds(30)).card("sha256:card-3").feature("device_id", "dv-9").insert();
        rig.row().at(t.minusSeconds(40)).card("sha256:card-4").feature("device_id", "dv-4").insert();
        rig.row().at(t.minus(Duration.ofDays(40))).card("sha256:card-1").insert(); // outside the 30-day window

        ObjectNode p = rig.fixture("txn.related.params--card.json");
        p.put("edgeAssessmentId", anchor.toString());
        p.put("depth", 1);
        TunnelOps.Result d1 = rig.run(rig.request("txn.related", p, 50, null, 8000, rig.actor()));
        assertEquals(List.of(sameCard.toString(), sameDevice.toString()), d1.items().stream()
                .map(n -> n.get("transaction").get("edgeAssessmentId").asText()).toList());
        assertEquals("CARD", d1.items().get(0).get("link").asText());
        assertEquals("DEVICE", d1.items().get(1).get("link").asText());
        for (ObjectNode n : d1.items()) {
            assertEquals(1, n.get("hop").asInt());
            assertEquals(anchor.toString(), n.get("viaEdgeAssessmentId").asText());
            assertFalse(n.toString().contains("card-1") || n.toString().contains("dv-1"), "no key values");
        }

        p.put("depth", 2);
        TunnelOps.Result d2 = rig.run(rig.request("txn.related", p, 50, null, 8000, rig.actor()));
        assertEquals(3, d2.items().size());
        ObjectNode hop2 = d2.items().get(2);
        assertEquals(viaHop1Device.toString(), hop2.get("transaction").get("edgeAssessmentId").asText());
        assertEquals(2, hop2.get("hop").asInt());
        assertEquals("DEVICE", hop2.get("link").asText());
        assertEquals(sameCard.toString(), hop2.get("viaEdgeAssessmentId").asText());
        assertNull(d2.incompleteReason());

        // Paging through depth 2 one item at a time yields the same sequence.
        List<String> paged = new ArrayList<>();
        String cursor = null;
        do {
            TunnelOps.Result r = rig.run(rig.request("txn.related", p, 1, cursor, 8000, rig.actor()));
            r.items().forEach(n -> paged.add(n.get("transaction").get("edgeAssessmentId").asText()));
            cursor = r.nextCursor();
        } while (cursor != null);
        assertEquals(d2.items().stream().map(n -> n.get("transaction").get("edgeAssessmentId").asText()).toList(), paged);
    }

    @Test
    void filingExtractReturnsTransactionsAndCtrAggregateInReportingCurrency() throws Exception {
        ZoneId nairobi = ZoneId.of("Africa/Nairobi");
        LocalDate day = rig.clock.instant().atZone(nairobi).toLocalDate().minusDays(1);
        Instant noon = day.atTime(12, 0).atZone(nairobi).toInstant();
        UUID anchor = rig.row().at(noon).card("sha256:cash-card").channel("ATM").amount(1_000_000, "KES").insert();
        UUID usd = rig.row().at(noon.plusSeconds(3600)).card("sha256:cash-card").channel("AGENT")
                .amount(1_500_000, "USD").insert();
        rig.row().at(noon.plusSeconds(60)).card("sha256:cash-card").channel("POS").amount(9_999_999, "USD").insert();
        rig.row().at(noon.plus(Duration.ofDays(1))).card("sha256:cash-card").channel("ATM").amount(5_000, "USD").insert();
        rig.row().at(noon).card("sha256:other").channel("ATM").amount(5_000, "USD").insert();

        ObjectNode p = rig.fixture("filing.extract.params--ctr.json");
        UUID absent = UUID.randomUUID();
        p.putArray("edgeAssessmentIds").add(anchor.toString()).add(absent.toString());
        TunnelOps.Result r = rig.run(rig.request("filing.extract", p, null, null, 15000, sysActor("CTR_FILING")));
        assertEquals(2, r.items().size());
        assertEquals("TRANSACTION", r.items().get(0).get("kind").asText());
        ObjectNode agg = r.items().get(1);
        assertEquals("CTR_AGGREGATE", agg.get("kind").asText());
        assertEquals(day.toString(), agg.get("businessDate").asText());
        assertEquals("USD", agg.get("reportingCurrency").asText());
        // 10 000.00 KES × 0.0077 USD/KES = 77.00 USD, plus 15 000.00 USD → 1 507 700 minor.
        assertEquals(1_507_700, agg.get("cashTotalMinor").asLong());
        assertEquals(1_500_000, agg.get("thresholdMinor").asLong());
        assertEquals(2, agg.get("txnCount").asInt());
        assertEquals(14, agg.get("configVersion").asLong());
        assertEquals(List.of(anchor.toString(), usd.toString()), toList(agg.get("edgeAssessmentIds")));
        assertEquals(List.of(absent.toString()), r.missing());
        assertTrue(r.returnedIds().containsAll(List.of(anchor, usd)));

        ObjectNode sar = p.deepCopy().put("kind", "SAR");
        TunnelOps.Result s = rig.run(rig.request("filing.extract", sar, null, null, 15000, sysActor("SAR_FILING")));
        assertEquals(1, s.items().size());
    }

    private ObjectNode sysActor(String purpose) {
        ObjectNode a = rig.mapper.createObjectNode();
        a.put("kind", "SYSTEM");
        a.put("userId", "system:filing");
        a.put("role", "SYSTEM");
        a.put("pspId", TunnelRig.PSP_ID);
        a.put("purpose", purpose);
        return a;
    }

    private static List<String> toList(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void exportStreamsChunksUpToMaxRowsWithProjection() throws Exception {
        Instant t = rig.clock.instant().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 1203; i++) {
            rig.row().at(t.minusMillis(i * 10L)).ref("EXP-" + i).decision("BLOCK", 90).insert();
        }
        ObjectNode p = rig.fixture("export.stream.params--month.json");
        ((ObjectNode) p.get("filter")).put("from", TunnelTime.millis(t.minus(Duration.ofDays(1))))
                .put("to", TunnelTime.millis(t.plusSeconds(1)));
        p.put("maxRows", 1_000_000);
        List<Long> totals = new ArrayList<>();
        List<List<ObjectNode>> chunks = rig.export(rig.request("export.stream", p, null, null, 300000,
                rig.actor().put("purpose", "DATA_EXPORT")), totals);
        assertEquals(List.of(500, 500, 203), chunks.stream().map(List::size).toList());
        assertEquals(List.of(-1L, -1L, 1203L), totals);
        ObjectNode item = chunks.get(0).get(0);
        assertEquals(Set.of("edgeAssessmentId", "evaluatedAt", "siteId", "decision", "txnRef", "merchantId",
                "amountMinor", "currency", "score"), fieldNames(item));

        p.put("maxRows", 700);
        totals.clear();
        chunks = rig.export(rig.request("export.stream", p, null, null, 300000, rig.actor().put("purpose", "DATA_EXPORT")),
                totals);
        assertEquals(List.of(500, 200), chunks.stream().map(List::size).toList());
        assertEquals(700L, totals.get(1));

        ((ObjectNode) p.get("filter")).put("from", TunnelTime.millis(t.minus(Duration.ofDays(367))));
        TunnelException e = assertThrows(TunnelException.class, () -> rig.export(rig.request("export.stream", p, null,
                null, 300000, rig.actor().put("purpose", "DATA_EXPORT")), new ArrayList<>()));
        assertEquals(TunnelErrorCode.PAGE_LIMIT_EXCEEDED, e.code());
    }

    private static Set<String> fieldNames(ObjectNode n) {
        Set<String> s = new HashSet<>();
        n.fieldNames().forEachRemaining(s::add);
        return s;
    }

    @Test
    void ledgerHealthIsSchemaValid() throws Exception {
        rig.row().insert();
        TunnelOps.Result r = rig.run(rig.request("ledger.health", rig.mapper.createObjectNode(), null, null, 3000,
                rig.actor()));
        ObjectNode item = r.items().get(0);
        assertEquals("OK", item.get("status").asText());
        assertEquals(TunnelRig.SITE_ID, item.get("siteId").asText());
        assertEquals(3, item.get("schemaVersion").asInt());
        assertEquals(5, item.get("retention").get("years").asInt());
        assertEquals(14, item.get("retention").get("configVersion").asInt());
        assertEquals(Jcs.sha256Hex(new byte[0]), item.get("holds").get("activeHoldDigest").asText());
        assertTrue(item.get("partitions").size() >= 4);
        assertNotNull(item.get("rows").get("newestEvaluatedAt"));
    }

    @Test
    void gatesRefuseOtherTenantsPlatformActorsAndDisallowedOps() throws Exception {
        ObjectNode params = rig.mapper.createObjectNode().put("edgeAssessmentId", UUID.randomUUID().toString());
        ObjectNode foreign = rig.actor().put("pspId", 99);
        assertEquals(TunnelErrorCode.TENANT_MISMATCH, assertThrows(TunnelException.class,
                () -> rig.run(rig.request("txn.get", params, null, null, 5000, foreign))).code());

        ObjectNode support = rig.fixture("request--support-access.json");
        assertEquals(TunnelErrorCode.SUPPORT_ACCESS_DISABLED, assertThrows(TunnelException.class,
                () -> rig.run(support)).code(), "default config has support access off");
        rig.acceptConfig(15, d -> ((ObjectNode) d.get("supportAccess")).put("enabled", true));
        assertEquals(TunnelErrorCode.NOT_FOUND, assertThrows(TunnelException.class,
                () -> rig.run(rig.fixture("request--support-access.json"))).code(), "consent given: the request runs");

        TunnelOps.Gate narrow = new TunnelOps.Gate(TunnelRig.PSP_ID, rig.config.current(), Set.of("txn.search"));
        assertEquals(TunnelErrorCode.OP_NOT_ALLOWED, assertThrows(TunnelException.class,
                () -> rig.ops.prepare(rig.opened(rig.request("txn.get", params, null, null, 5000, rig.actor())), narrow))
                .code());

        ObjectNode late = rig.request("txn.get", params, null, null, 100, rig.actor());
        TunnelFrameCodec.Opened old = new TunnelFrameCodec.Opened("request", UUID.randomUUID().toString(), null,
                UUID.randomUUID().toString(), "n", rig.clock.instant().minusSeconds(5), late);
        assertEquals(TunnelErrorCode.DEADLINE_EXCEEDED, assertThrows(TunnelException.class,
                () -> rig.ops.execute(rig.ops.prepare(old, rig.gate()))).code());
    }

    @Test
    void backfillImportsIdempotentlyAndVerifies() throws Exception {
        ObjectNode batchReq = rig.fixture("request--ledger-backfill-batch.json");
        assertEquals(TunnelErrorCode.BACKFILL_NOT_ALLOWED, assertThrows(TunnelException.class,
                () -> rig.run(batchReq)).code(), "the default config does not allow backfill");
        rig.acceptConfig(15, TunnelRig::allowAllOps);

        UUID runId = UUID.randomUUID();
        ObjectNode begin = rig.fixture("ledger.backfill.params--begin.json").put("backfillId", runId.toString())
                .put("expectedRows", 3);
        assertEquals("OPEN", backfill(begin).get("state").asText());
        assertEquals("OPEN", backfill(begin).get("state").asText(), "an identical BEGIN is idempotent");
        assertEquals(TunnelErrorCode.BACKFILL_REJECTED, assertThrows(TunnelException.class,
                () -> backfill(begin.deepCopy().put("expectedRows", 4))).code());

        ObjectNode params = (ObjectNode) batchReq.get("params");
        params.put("backfillId", runId.toString());
        ArrayNode rows = (ArrayNode) params.get("rows");
        ObjectNode ok = (ObjectNode) rows.get(0);
        ObjectNode second = ok.deepCopy().put("legacyTransactionId", "txn-55121")
                .put("edgeAssessmentId", UUID.randomUUID().toString()).put("txnRef", "AUTH-OTHER-1");
        ObjectNode pastRetention = ok.deepCopy().put("legacyTransactionId", "txn-1").put("evaluatedAt",
                "2019-01-01T00:00:00Z").put("edgeAssessmentId", UUID.randomUUID().toString()).put("txnRef", "AUTH-OLD");
        ObjectNode rawPan = ok.deepCopy().put("legacyTransactionId", "txn-2").put("panHash", "4111111111111111")
                .put("edgeAssessmentId", UUID.randomUUID().toString());
        ObjectNode refClash = ok.deepCopy().put("legacyTransactionId", "txn-3")
                .put("edgeAssessmentId", UUID.randomUUID().toString());
        rows.add(second).add(pastRetention).add(rawPan).add(refClash);
        ObjectNode ack = rig.run(batchReq).items().get(0);
        assertEquals(2, ack.get("accepted").asInt());
        assertEquals(0, ack.get("duplicates").asInt());
        assertEquals(Set.of("txn-1:PAST_RETENTION", "txn-2:SCHEMA_INVALID", "txn-3:TXN_REF_CONFLICT"),
                rejections(ack));
        assertFalse(ack.toString().contains("sha256:9c11"), "acks never echo row content");

        // A re-sent batch is all duplicates; a different row under a taken id is ID_CONFLICT.
        ObjectNode resend = rig.fixture("request--ledger-backfill-batch.json");
        ((ObjectNode) resend.get("params")).put("backfillId", runId.toString());
        ((ArrayNode) resend.get("params").get("rows")).add(second.deepCopy());
        ObjectNode ack2 = rig.run(resend).items().get(0);
        assertEquals(0, ack2.get("accepted").asInt());
        assertEquals(2, ack2.get("duplicates").asInt());
        ObjectNode conflict = rig.fixture("request--ledger-backfill-batch.json");
        ((ObjectNode) conflict.get("params")).put("backfillId", runId.toString()).put("batchSeq", 1);
        ((ObjectNode) conflict.get("params").get("rows").get(0)).put("score", 99).put("legacyTransactionId", "txn-9");
        assertEquals(Set.of("txn-9:ID_CONFLICT"), rejections(rig.run(conflict).items().get(0)));

        ObjectNode skip = rig.fixture("request--ledger-backfill-batch.json");
        ((ObjectNode) skip.get("params")).put("backfillId", runId.toString()).put("batchSeq", 5);
        assertEquals(TunnelErrorCode.BACKFILL_REJECTED, assertThrows(TunnelException.class, () -> rig.run(skip)).code());

        // The imported row is readable, BACKFILL / cloud-legacy, with hygienic features.
        ObjectNode get = rig.mapper.createObjectNode().put("edgeAssessmentId", ok.get("edgeAssessmentId").asText());
        ObjectNode detail = rig.run(rig.request("txn.get", get, null, null, 5000, rig.actor())).items().get(0);
        assertEquals("BACKFILL", detail.get("origin").asText());
        assertEquals("cloud-legacy", detail.get("evaluator").asText());
        assertEquals(-1, detail.get("bundleVersion").asInt());

        String digest = Jcs.sha256Hex(String.join("\n", List.of("txn-55120", "txn-55121")).getBytes(StandardCharsets.UTF_8));
        ObjectNode commit = rig.fixture("ledger.backfill.params--commit.json").put("backfillId", runId.toString())
                .put("expectedRows", 2).put("expectedIdDigest", digest);
        ObjectNode verified = backfill(commit);
        assertEquals("VERIFIED", verified.get("state").asText());
        assertEquals(digest, verified.get("idDigest").asText());
        ObjectNode mismatch = commit.deepCopy().put("expectedRows", 3);
        UUID run2 = UUID.randomUUID();
        backfill(begin.deepCopy().put("backfillId", run2.toString()));
        assertEquals("VERIFY_MISMATCH", backfill(mismatch.put("backfillId", run2.toString())).get("state").asText());
        ObjectNode abort = rig.mapper.createObjectNode().put("phase", "ABORT").put("backfillId", run2.toString())
                .put("reason", "OPERATOR");
        assertEquals("ABORTED", backfill(abort).get("state").asText());
        ObjectNode unknown = abort.deepCopy().put("backfillId", UUID.randomUUID().toString());
        assertEquals(TunnelErrorCode.BACKFILL_REJECTED, assertThrows(TunnelException.class, () -> backfill(unknown)).code());

        try (Connection c = LedgerTestDatabase.connect(rig.schema); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM edge_evaluations WHERE origin = 'BACKFILL'")) {
            rs.next();
            assertEquals(2, rs.getInt(1), "insert-only: exactly the accepted rows exist");
        }
    }

    private ObjectNode backfill(ObjectNode params) throws TunnelException {
        ObjectNode actor = rig.mapper.createObjectNode().put("kind", "SYSTEM").put("userId", "system:backfill")
                .put("role", "SYSTEM").put("pspId", TunnelRig.PSP_ID).put("purpose", "LEDGER_BACKFILL");
        return rig.run(rig.request("ledger.backfill", params, null, null, 60000, actor)).items().get(0);
    }

    private static Set<String> rejections(ObjectNode ack) {
        Set<String> out = new HashSet<>();
        ack.get("rejected").forEach(r -> out.add(r.get("legacyTransactionId").asText() + ":" + r.get("code").asText()));
        return out;
    }
}
