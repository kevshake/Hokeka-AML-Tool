package com.hokeka.edge.sanctions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.EdgeRuleInterpreter;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.ledger.LedgerEvaluation;
import com.hokeka.edge.ledger.LedgerEvaluations;
import com.hokeka.edge.ledger.LedgerTestDatabase;
import com.hokeka.edge.tunnel.TunnelRig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R10b on a real Edge Ledger (V3): the list is stored and re-verified, survives a restart, a tampered
 * stored chunk is refused, and the evaluate-path screener sets the decision record v2 sanctions fields
 * and takes the contract action (confirmed BLOCK, potential HOLD, PEP no hit, blocklisted merchant
 * BLOCK, list missing HOLD, fail-closed unchanged).
 */
class SanctionsListLedgerIntegrationTest {

    private TunnelRig rig;
    private final ObjectMapper mapper = new ObjectMapper();
    private SanctionsTestLists.Built list;

    @BeforeEach
    void setUp() throws Exception {
        rig = new TunnelRig();
        list = SanctionsTestLists.build(List.of(
                SanctionsTestLists.entry("us-ofac-sdn:NK-1001", "Ivan Petrovich Sidorov",
                        List.of("Ivan Sidorov", "Iwan Sidorow"), "PERSON", null),
                SanctionsTestLists.entry("gb-hmt:7788", "Northwind Shipping Company Limited",
                        List.of("Northwind Shipping"), "ORGANIZATION", null),
                SanctionsTestLists.entry("peps:Q44", "Wanjiru Kamau", List.of(), "PERSON", "PEP")),
                2, "20261017-0200", "2026-10-17T02:00:00Z");
    }

    @AfterEach
    void tearDown() {
        rig.close();
    }

    /** Stores the list as the fetcher does and activates it. */
    private SanctionsListStore storeAndActivate() throws Exception {
        SanctionsListStore store = newStore();
        store.saveManifest(list.manifest);
        for (int i = 0; i < list.chunks.size(); i++) {
            ObjectNode c = list.chunks.get(i);
            store.saveChunk(list.listHash, i, c.get("sha256").asText(), c.get("entryCount").asInt(), list.gzips.get(i));
        }
        store.activate(list.listHash, Instant.now());
        return store;
    }

    /** A store on the rig's ledger preferring the config's list. */
    private SanctionsListStore newStore() {
        return new SanctionsListStore(rig.ledger, mapper, 0.8,
                () -> rig.config.current().map(d -> d.sanctionsListHash()));
    }

    /** Accepts a config that names the list and blocks merchant mrc_blocked. */
    private void acceptConfigNamingTheList(long version) {
        rig.acceptConfig(version, doc -> {
            ObjectNode s = (ObjectNode) doc.get("sanctions");
            s.put("listVersion", "20261017-0200");
            s.put("listHash", list.listHash);
            s.put("publishedAt", "2026-10-17T02:00:00Z");
            ObjectNode block = ((ArrayNode) doc.get("merchantBlocklist").get("entries")).addObject();
            block.put("merchantId", "mrc_blocked");
            block.put("reasonCode", "SANCTIONED");
            block.put("since", "2026-10-17T03:00:00Z");
        });
    }

    @Test
    void theStoredListSurvivesARestart() throws Exception {
        acceptConfigNamingTheList(30);
        SanctionsListStore first = storeAndActivate();
        assertEquals(3, first.current().orElseThrow().entryCount());

        SanctionsListStore restarted = newStore();
        assertTrue(restarted.current().isEmpty(), "nothing in memory before the restore");
        restarted.restoreIfNeeded();
        SanctionsListStore.Loaded loaded = restarted.current().orElseThrow();
        assertEquals(list.listHash, loaded.listHash());
        assertEquals("20261017-0200", loaded.listVersion());
        assertEquals("us-ofac-sdn:NK-1001", loaded.index().screen("Ivan Sidorov").get(0).entry().id());
    }

    @Test
    void aTamperedStoredChunkIsRefusedOnRestore() throws Exception {
        storeAndActivate();
        try (Connection c = LedgerTestDatabase.connect(rig.schema);
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE edge_sanctions_list_chunks SET content_gzip = ? WHERE chunk_index = 1")) {
            ps.setBytes(1, SanctionsTestLists.gzip(
                    "{\"id\":\"x-y:1\",\"name\":\"Someone Else\",\"aliases\":[],\"type\":\"PERSON\"}\n".getBytes()));
            ps.executeUpdate();
        }
        SanctionsListStore restarted = newStore();
        restarted.restoreIfNeeded();
        assertTrue(restarted.current().isEmpty(), "a list whose chunk no longer matches its hash is never used");
        assertTrue(restarted.lastError().contains("sha256"), restarted.lastError());
        assertThrows(SanctionsListCodec.InvalidListException.class,
                () -> restarted.activate("0".repeat(64), Instant.now()), "an unknown list cannot be activated");
    }

    @Test
    void aChunkThatDoesNotMatchItsManifestLineIsRefused() throws Exception {
        SanctionsListPoller poller = new SanctionsListPoller(null, null, null, null, null, null, null, mapper, null);
        ObjectNode line = (ObjectNode) list.manifest.get("chunks").get(0);
        assertEquals(list.gzips.get(0).length, poller.verifyChunk(list.chunks.get(0), list.listHash, line).length);
        ObjectNode wrongIndex = list.chunks.get(1);
        assertThrows(SanctionsListCodec.InvalidListException.class,
                () -> poller.verifyChunk(wrongIndex, list.listHash, line));
        ObjectNode wrongData = list.chunks.get(0).deepCopy();
        wrongData.put("data", java.util.Base64.getEncoder().encodeToString(list.gzips.get(1)));
        assertThrows(SanctionsListCodec.InvalidListException.class,
                () -> poller.verifyChunk(wrongData, list.listHash, line), "sha256 of the content is checked");
        ObjectNode tamperedManifest = list.manifest.deepCopy();
        ((ObjectNode) tamperedManifest.get("chunks").get(0)).put("entryCount", 1);
        assertThrows(SanctionsListCodec.InvalidListException.class,
                () -> SanctionsListCodec.verifyManifest(tamperedManifest), "listHash binds the chunk lines");
    }

    @Test
    void screeningSetsTheDecisionRecordSanctionsFieldsAndTheContractAction() throws Exception {
        acceptConfigNamingTheList(31);
        SanctionsListStore store = storeAndActivate();
        SanctionsScreener screener = new SanctionsScreener(store, rig.config, true);

        // Confirmed hit (score 1.0 on an alias) → BLOCK, sanctionsHit, entry id; the name stays local.
        SanctionsScreener.Result confirmed = screener.screen(features("counterparty_name", "Ivan Sidorov"));
        assertEquals(EdgeRuleInterpreter.Action.BLOCK, confirmed.escalation());
        assertEquals(1, confirmed.hits().size());
        Map<String, Object> hit = confirmed.hits().get(0);
        assertEquals("us-ofac-sdn:NK-1001", hit.get("entryId"));
        assertEquals("COUNTERPARTY_NAME", hit.get("matchedOn"));
        assertEquals(1.0, hit.get("score"));
        assertEquals("20261017-0200", hit.get("listVersion"));
        EdgeEngine.Outcome allow = new EdgeEngine.Outcome(new EdgeRuleInterpreter.Decision(EdgeRuleInterpreter.Action.ALLOW,
                0, List.of(), List.of()), EdgeEngine.CONTRACT_EVALUATOR_JAVA, null);
        EdgeEngine.Outcome blocked = SanctionsScreener.apply(allow, confirmed);
        assertEquals(EdgeRuleInterpreter.Action.BLOCK, blocked.decision().action());
        LedgerEvaluation row = LedgerEvaluations.fromEvaluation(UUID.randomUUID(), Instant.now(), TunnelRig.SITE_ID,
                TunnelRig.EDGE_ID, features("counterparty_name", "Ivan Sidorov"), null, blocked.decision(), -1, null,
                "java", null, 900).withSanctionsHits(confirmed.hits());
        ObjectNode record = LedgerEvaluations.decisionRecord(row, mapper);
        assertTrue(record.get("sanctionsHit").asBoolean());
        assertEquals("us-ofac-sdn:NK-1001", record.get("watchlistEntryIds").get(0).asText());
        assertEquals("BLOCK", record.get("decision").asText());
        assertEquals(List.of(), rig.schemas.validate(ContractSchemas.DECISION_RECORD_V2, record));
        assertFalse(record.toString().contains("Sidorov"), "no name in the decision record: " + record);

        // Potential hit (0.9167) on the beneficiary → HOLD.
        SanctionsScreener.Result potential = screener.screen(features("beneficiary_name", "Iwan Sydorow"));
        assertEquals(EdgeRuleInterpreter.Action.HOLD, potential.escalation());
        assertEquals("BENEFICIARY_NAME", potential.hits().get(0).get("matchedOn"));
        assertEquals(0.9167, potential.hits().get(0).get("score"));

        // A PEP match is not a sanctions hit and changes nothing but the local reasons.
        SanctionsScreener.Result pep = screener.screen(features("cardholder_name", "Wanjiru Kamau"));
        assertTrue(pep.hits().isEmpty());
        assertNull(pep.escalation());
        assertTrue(pep.reasons().get(0).contains("peps:Q44"), pep.reasons().toString());
        assertSame(EdgeRuleInterpreter.Action.ALLOW, SanctionsScreener.apply(allow, pep).decision().action());

        // A blocklisted merchant is refused even without a name.
        Map<String, Object> merchant = new LinkedHashMap<>();
        merchant.put("merchant_id", "mrc_blocked");
        assertEquals(EdgeRuleInterpreter.Action.BLOCK, screener.screen(merchant).escalation());
        merchant.put("merchant_id", "mrc_fine");
        assertEquals(SanctionsScreener.Result.NONE, screener.screen(merchant));

        // A fail-closed HOLD stays a HOLD (schema), but the hits are still recorded.
        EdgeEngine.Outcome failClosed = new EdgeEngine.Outcome(new EdgeRuleInterpreter.Decision(
                EdgeRuleInterpreter.Action.HOLD, 0, List.of(), List.of("fs down")), EdgeEngine.CONTRACT_EVALUATOR_NONE,
                "FEATURE_STORE_UNAVAILABLE");
        assertSame(failClosed, SanctionsScreener.apply(failClosed, confirmed));

        // Clean name → nothing.
        assertEquals(List.of(), screener.screen(features("counterparty_name", "Peter Parker")).hits());
    }

    @Test
    void aPublishedListThatIsNotLoadedHoldsEvaluationsWithNames() {
        acceptConfigNamingTheList(32);
        SanctionsListStore empty = newStore();
        SanctionsScreener screener = new SanctionsScreener(empty, rig.config, true);
        SanctionsScreener.Result held = screener.screen(features("counterparty_name", "Anyone"));
        assertEquals(EdgeRuleInterpreter.Action.HOLD, held.escalation());
        assertTrue(held.hits().isEmpty(), "no list, no hit claimed");
        assertNull(new SanctionsScreener(empty, rig.config, false).screen(features("counterparty_name", "Anyone"))
                .escalation(), "the hold can be switched off");
        assertEquals(SanctionsScreener.Result.NONE, screener.screen(features("amount_cents", 100)),
                "nothing to screen, nothing held");
        assertEquals(Optional.empty(), empty.current());
    }

    /** One-feature bag. */
    private static Map<String, Object> features(String key, Object value) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put(key, value);
        return f;
    }
}
