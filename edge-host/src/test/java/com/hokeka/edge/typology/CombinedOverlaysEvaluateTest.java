package com.hokeka.edge.typology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeController;
import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.EdgeEvaluation;
import com.hokeka.edge.EdgeRuleInterpreter.Action;
import com.hokeka.edge.activation.AuthorizationGate;
import com.hokeka.edge.channel.EdgeMetricsAggregator;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.ledger.EdgeLedger;
import com.hokeka.edge.ledger.EvaluationRecorder;
import com.hokeka.edge.ledger.LedgerEvaluation;
import com.hokeka.edge.ledger.LedgerEvaluations;
import com.hokeka.edge.ledger.LedgerProperties;
import com.hokeka.edge.residency.EdgeConfigDocument;
import com.hokeka.edge.residency.EdgeConfigStore;
import com.hokeka.edge.sanctions.SanctionsEntry;
import com.hokeka.edge.sanctions.SanctionsIndex;
import com.hokeka.edge.sanctions.SanctionsListStore;
import com.hokeka.edge.sanctions.SanctionsScreener;
import com.hokeka.edge.store.AerospikeFeatureStore;
import com.hokeka.edge.store.FakeAerospike;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code POST /edge/evaluate} end to end with several overlays applying to one transaction at
 * once: R10b sanctions screening and merchant blocklist, the R10a compliance overlay (CTR, limits)
 * and the compiled SpEL catalog rules. The final action must be the strongest of all of them, a
 * BLOCK from any source must consume no limit, and the decision record must carry the sanctions
 * and the CTR/limit fields together while validating against {@code decision-record-v2}.
 */
class CombinedOverlaysEvaluateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** The fixture config's blocklisted merchant ({@code edge-config--extended-retention.json}). */
    private static final String BLOCKED_MERCHANT = "mrc_09917";
    private static final String SDN_ID = "us-ofac-sdn:NK-1001";
    private static final String PEP_ID = "peps:Q44";

    private final List<LedgerEvaluation> recorded = new CopyOnWriteArrayList<>();
    private final ContractSchemas schemas =
            ContractSchemas.fromDirectory(Path.of(System.getProperty("contracts.dir", "../docs/contracts")));
    private final Map<String, Long> ruleIds = new HashMap<>();
    private byte[] bundle;
    private FakeAerospike aerospike;

    /** Loads the compiled catalog and indexes its rule ids by name. */
    @BeforeEach
    void loadCatalog() throws Exception {
        bundle = Files.readAllBytes(Path.of("..", "edge-engine", "test-vectors", "typology_catalog_bundle.json"));
        for (JsonNode r : MAPPER.readTree(bundle).get("rules")) {
            ruleIds.put(r.get("name").asText(), r.get("id").asLong());
        }
    }

    /** Rule id of a catalog rule by (a fragment of) its name. */
    private long rule(String nameFragment) {
        return ruleIds.entrySet().stream().filter(e -> e.getKey().contains(nameFragment)).findFirst()
                .orElseThrow(() -> new AssertionError("no rule named like " + nameFragment)).getValue();
    }

    /** Deliberate test double (intentional-stub): captures ledger rows instead of writing them. */
    private EvaluationRecorder capturingRecorder() {
        ObjectMapper mapper = new ObjectMapper();
        return new EvaluationRecorder(new EdgeLedger(new LedgerProperties(), mapper, "edge"), schemas, mapper) {
            @Override
            public LedgerEvaluation record(LedgerEvaluation ev) {
                recorded.add(ev);
                return ev;
            }
        };
    }

    /**
     * The in-memory sanctions list in force: one sanctioned person (with aliases) and one PEP, the
     * same entries the R10b ledger test publishes, at the default 0.8 similarity threshold.
     */
    private static SanctionsListStore loadedList() {
        SanctionsIndex index = new SanctionsIndex(List.of(
                new SanctionsEntry(SDN_ID, "Ivan Petrovich Sidorov", List.of("Ivan Sidorov", "Iwan Sidorow"), "PERSON", null),
                new SanctionsEntry(PEP_ID, "Wanjiru Kamau", List.of(), "PERSON", "PEP")), 0.8);
        SanctionsListStore lists = mock(SanctionsListStore.class);
        when(lists.current()).thenReturn(Optional.of(new SanctionsListStore.Loaded("a".repeat(64), "2026-10-16.1",
                Instant.parse("2026-10-16T05:00:00Z"), 2, index, Instant.now())));
        return lists;
    }

    /**
     * A controller over a fresh site store, the catalog bundle, the config and the sanctions
     * screener wired as Spring wires them.
     */
    private EdgeController controller(EdgeConfigDocument config) {
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized());
        engine.loadPlainBundle(bundle);
        aerospike = new FakeAerospike();
        EdgeController c = new EdgeController(engine, new EdgeMetricsAggregator("psp", "edge", 64, 1_000L),
                new AerospikeFeatureStore(aerospike.client, "hokeka", "site-a"), capturingRecorder(), "edge-1", "site-a");
        ReflectionTestUtils.setField(c, "failClosedOnStoreUnavailable", true);
        EdgeConfigStore store = mock(EdgeConfigStore.class);
        when(store.current()).thenReturn(Optional.of(config));
        c.setLocalResidencyStatus(store, null, null);
        SanctionsListStore lists = loadedList();
        c.setSanctions(new SanctionsScreener(lists, store, true), lists);
        return c;
    }

    /** Evaluates one request. */
    private static EdgeEvaluation evaluate(EdgeController c, Map<String, Object> body) {
        return (EdgeEvaluation) c.evaluate(new LinkedHashMap<>(body)).getBody();
    }

    /** An identified card-present request (so the R-170 anonymity rule stays quiet). */
    private static Map<String, Object> base(String pan) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pan_hash", pan);
        m.put("merchant_id", "m-e2");
        m.put("currency", "USD");
        m.put("country_code", "KE");
        m.put("mcc", "5732");
        m.put("customer_id", "cust-1");
        return m;
    }

    /** The last recorded decision record, schema-validated and free of names and amounts. */
    private JsonNode lastRecord() {
        JsonNode record = LedgerEvaluations.decisionRecord(recorded.get(recorded.size() - 1), MAPPER);
        List<String> violations = schemas.validate("decision-record-v2.schema.json", record);
        assertTrue(violations.isEmpty(), "decision record v2 must validate: " + violations);
        String text = record.toString();
        for (String forbidden : List.of("Sydorow", "Sidorov", "Kamau", "amount", "currency", "pan_hash", "features")) {
            assertFalse(text.contains(forbidden), forbidden + " must not reach the decision record: " + text);
        }
        return record;
    }

    /** The typology codes of a decision record (empty when absent). */
    private static List<String> codes(JsonNode record) {
        return record.has("typologyCodes")
                ? MAPPER.convertValue(record.get("typologyCodes"), MAPPER.getTypeFactory().constructCollectionType(List.class, String.class))
                : List.of();
    }

    /** Limit usage counted so far for one limit id (sum of the {@code n} bins of its buckets). */
    private long limitUsage(long limitId) {
        long n = 0;
        for (String key : aerospike.keys(ComplianceOverlay.SET_LIMITS)) {
            if (key.contains("L" + limitId + ":")) {
                Object v = aerospike.bins(ComplianceOverlay.SET_LIMITS, key).get("n");
                n += v instanceof Number num ? num.longValue() : 0L;
            }
        }
        return n;
    }

    /**
     * A potential sanctions hit on the beneficiary (HOLD) on a cash transaction that takes the
     * card-day total over the CTR threshold: HOLD, both sets of reasons, and a record carrying
     * sanctionsHit + watchlistEntryIds together with ctrRequired + CTR_REPORTABLE.
     */
    @Test
    void sanctionsHoldAndCtrCrossingAreBothRecorded() {
        EdgeController c = controller(TypologyFixtures.config(d -> { }));
        Map<String, Object> b = base("ph-combo-ctr");
        b.put("txn_id", "combo-ctr-1");
        b.put("channel", "ATM");
        b.put("amount_cents", 1_600_000L);
        b.put("beneficiary_name", "Iwan Sydorow");

        EdgeEvaluation d = evaluate(c, b);

        assertEquals(Action.HOLD, d.action(), d.reasons().toString());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("watchlist entry " + SDN_ID)
                && r.contains("potential hit, held for review")), d.reasons().toString());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("reached the CTR threshold")), d.reasons().toString());
        JsonNode record = lastRecord();
        assertEquals("HOLD", record.get("decision").asText());
        assertTrue(record.get("sanctionsHit").asBoolean());
        assertEquals(SDN_ID, record.get("watchlistEntryIds").get(0).asText());
        assertEquals(1, record.get("watchlistEntryIds").size());
        assertTrue(record.get("ctrRequired").asBoolean());
        assertTrue(codes(record).contains("CTR_REPORTABLE"), codes(record).toString());
        assertFalse(record.has("failClosedCode"));
    }

    /**
     * A blocklisted merchant on a card whose limit is already used up: BLOCK (the strongest of the
     * blocklist BLOCK and the limit's HOLD), both reasons, LIMIT_ code in the record, and no limit
     * usage consumed by the BLOCKed transaction. A second, roomier limit proves the blocked
     * transaction was not counted: the next allowed transaction stays inside it.
     */
    @Test
    void blocklistedMerchantBlocksAndConsumesNoLimit() {
        EdgeController c = controller(TypologyFixtures.config(d -> {
            TypologyFixtures.limit(d, 41, "CARD", "COUNT", "PT1H", null, 1, "HOLD");
            TypologyFixtures.limit(d, 42, "CARD", "COUNT", "PT1H", null, 2, "ALERT");
        }));
        Map<String, Object> first = base("ph-combo-limit");
        first.put("amount_cents", 1001);
        EdgeEvaluation allowed = evaluate(c, first);
        assertFalse(allowed.action() == Action.BLOCK, allowed.reasons().toString());
        assertEquals(1, limitUsage(41));
        assertEquals(1, limitUsage(42));

        Map<String, Object> blocked = base("ph-combo-limit");
        blocked.put("amount_cents", 1001);
        blocked.put("merchant_id", BLOCKED_MERCHANT);
        EdgeEvaluation d = evaluate(c, blocked);

        assertEquals(Action.BLOCK, d.action(), d.reasons().toString());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("merchant blocklist")), d.reasons().toString());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("limit 41 exceeded")), d.reasons().toString());
        JsonNode record = lastRecord();
        assertEquals("BLOCK", record.get("decision").asText());
        assertEquals(BLOCKED_MERCHANT, record.get("merchantId").asText());
        assertTrue(codes(record).contains("LIMIT_41"), codes(record).toString());
        assertFalse(codes(record).contains("LIMIT_42"), "1 used + 1 is within limit 42");
        assertFalse(record.get("sanctionsHit").asBoolean());
        assertFalse(record.get("ctrRequired").asBoolean());
        assertEquals(1, limitUsage(41), "a BLOCKed transaction consumes no limit");
        assertEquals(1, limitUsage(42), "a BLOCKed transaction consumes no limit");

        Map<String, Object> next = base("ph-combo-limit");
        next.put("amount_cents", 1001);
        evaluate(c, next);
        List<String> nextCodes = codes(lastRecord());
        assertTrue(nextCodes.contains("LIMIT_41"), nextCodes.toString());
        assertFalse(nextCodes.contains("LIMIT_42"), "the blocked transaction was not counted: " + nextCodes);
        assertEquals(2, limitUsage(42));
    }

    /**
     * A confirmed sanctions hit (BLOCK) does not consume limit usage either: screening runs before
     * the overlay commits, so the commit sees the final BLOCK.
     */
    @Test
    void confirmedSanctionsBlockConsumesNoLimit() {
        EdgeController c = controller(TypologyFixtures.config(d ->
                TypologyFixtures.limit(d, 51, "CARD", "COUNT", "PT1H", null, 5, "HOLD")));
        Map<String, Object> b = base("ph-combo-sdn");
        b.put("amount_cents", 1001);
        b.put("counterparty_name", "Ivan Sidorov");

        EdgeEvaluation d = evaluate(c, b);

        assertEquals(Action.BLOCK, d.action(), d.reasons().toString());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("confirmed hit, blocked")), d.reasons().toString());
        JsonNode record = lastRecord();
        assertEquals("BLOCK", record.get("decision").asText());
        assertTrue(record.get("sanctionsHit").asBoolean());
        assertEquals(SDN_ID, record.get("watchlistEntryIds").get(0).asText());
        assertEquals(0, limitUsage(51), "a sanctions BLOCK consumes no limit");
    }

    /**
     * A catalog rule ALERT (R-14 high-risk country) with a PEP match on the cardholder: ALERT stays
     * ALERT (a PEP is enhanced due diligence, not a sanctions hit), the PEP reason is added, and
     * the record has no sanctions hit.
     */
    @Test
    void ruleAlertWithPepReasonStaysAlert() {
        EdgeController c = controller(TypologyFixtures.config(d -> { }));
        Map<String, Object> b = base("ph-combo-pep");
        b.put("txn_id", "combo-pep-1");
        b.put("country_code", "ir");
        b.put("amount_cents", 1001);
        b.put("cardholder_name", "Wanjiru Kamau");

        EdgeEvaluation d = evaluate(c, b);

        long highRisk = rule("High-Risk Country Transaction");
        assertTrue(d.triggeredRuleIds().contains(highRisk), d.triggeredRuleIds().toString());
        assertEquals(Action.ALERT, d.action(), d.reasons().toString());
        assertTrue(d.reasons().stream().anyMatch(r -> r.startsWith("PEP screening") && r.contains(PEP_ID)
                && r.contains("enhanced due diligence, not a sanctions hit")), d.reasons().toString());
        JsonNode record = lastRecord();
        assertEquals("ALERT", record.get("decision").asText());
        assertFalse(record.get("sanctionsHit").asBoolean());
        assertFalse(record.has("watchlistEntryIds"));
        assertFalse(record.get("ctrRequired").asBoolean());
        assertTrue(record.get("triggeredRuleIds").toString().contains(String.valueOf(highRisk)));
    }

    /**
     * A feature-store outage keeps the fail-closed HOLD even with a confirmed sanctions hit, and the
     * hit still reaches the decision record (R10b guarantee kept on the R10a fail-closed path).
     */
    @Test
    void failClosedHoldStaysHoldWithSanctionsHitRecorded() {
        EdgeController c = controller(TypologyFixtures.config(d -> { }));
        aerospike.setDown(true);
        Map<String, Object> b = base("ph-combo-down");
        b.put("amount_cents", 1001);
        b.put("counterparty_name", "Ivan Sidorov");

        EdgeEvaluation d = evaluate(c, b);

        assertEquals(Action.HOLD, d.action());
        assertTrue(d.triggeredRuleIds().isEmpty());
        JsonNode record = lastRecord();
        assertEquals("HOLD", record.get("decision").asText());
        assertEquals("FEATURE_STORE_UNAVAILABLE", record.get("failClosedCode").asText());
        assertTrue(record.get("sanctionsHit").asBoolean());
        assertEquals(SDN_ID, record.get("watchlistEntryIds").get(0).asText());
    }
}
