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
import com.hokeka.edge.store.AerospikeFeatureStore;
import com.hokeka.edge.store.FakeAerospike;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * {@code POST /edge/evaluate} end to end per typology: the controller with the site store
 * (in-memory Aerospike), the compliance overlay and the engine armed with the seeded SpEL catalog
 * exactly as BACKEND's {@code EdgeRuleCompiler} compiles it
 * ({@code edge-engine/test-vectors/typology_catalog_bundle.json}, written by BACKEND's
 * {@code EdgeTypologyCatalogIntegrationTest}). Every decision record produced is validated against
 * the closed {@code decision-record-v2} schema.
 */
class EdgeTypologyEvaluateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final List<LedgerEvaluation> recorded = new CopyOnWriteArrayList<>();
    private final ContractSchemas schemas =
            ContractSchemas.fromDirectory(Path.of(System.getProperty("contracts.dir", "../docs/contracts")));
    private final Map<String, Long> ruleIds = new HashMap<>();
    private byte[] bundle;

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

    /** A controller over a fresh site store, the catalog bundle and an optional config. */
    private EdgeController controller(EdgeConfigDocument config) {
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized());
        engine.loadPlainBundle(bundle);
        FakeAerospike as = new FakeAerospike();
        EdgeController c = new EdgeController(engine, new EdgeMetricsAggregator("psp", "edge", 64, 1_000L),
                new AerospikeFeatureStore(as.client, "hokeka", "site-a"), capturingRecorder(), "edge-1", "site-a");
        ReflectionTestUtils.setField(c, "failClosedOnStoreUnavailable", true);
        if (config != null) {
            EdgeConfigStore store = mock(EdgeConfigStore.class);
            when(store.current()).thenReturn(Optional.of(config));
            c.setLocalResidencyStatus(store, null, null);
        }
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

    /** The last recorded decision record, schema-validated. */
    private JsonNode lastRecord() {
        JsonNode record = LedgerEvaluations.decisionRecord(recorded.get(recorded.size() - 1), MAPPER);
        List<String> violations = schemas.validate("decision-record-v2.schema.json", record);
        assertTrue(violations.isEmpty(), "decision record v2 must validate: " + violations);
        return record;
    }

    /** Simulator E2: a 12-transaction burst on one card now ALERTs (R-172 at 8, R-30 at 10). */
    @Test
    void velocityBurstFromScenarioE2Alerts() {
        EdgeController c = controller(null);
        List<EdgeEvaluation> burst = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Map<String, Object> b = base("ph-vel-e2");
            b.put("txn_id", "v" + i);
            b.put("amount_cents", 1001 + i);
            burst.add(evaluate(c, b));
        }
        long oneMinute = rule("PAN velocity 1 minute");
        long day = rule("Transaction Velocity (count in time)");
        assertFalse(burst.get(6).triggeredRuleIds().contains(oneMinute), "7 in a minute is under the threshold");
        assertTrue(burst.get(7).triggeredRuleIds().contains(oneMinute), "the 8th within a minute trips R-172");
        assertFalse(burst.get(8).triggeredRuleIds().contains(day));
        assertTrue(burst.get(9).triggeredRuleIds().contains(day), "the 10th in 24 h trips R-30");
        assertTrue(burst.get(11).action().ordinal() >= Action.ALERT.ordinal(), "the burst no longer stays ALLOW");
        assertTrue(recorded.size() == 12);
        assertEquals("ALERT", lastRecord().get("decision").asText());
    }

    /** R-8: five outbound amounts just under 9 000 within 24 h. */
    @Test
    void structuringJustBelowThresholdAlerts() {
        EdgeController c = controller(null);
        long outgoing = rule("Outgoing Sub-Threshold");
        EdgeEvaluation last = null;
        for (int i = 0; i < 5; i++) {
            Map<String, Object> b = base("ph-struct");
            b.put("amount_cents", 850_000 + i);
            last = evaluate(c, b);
            assertEquals(i == 4, last.triggeredRuleIds().contains(outgoing), "transaction " + (i + 1));
        }
        assertTrue(last.action().ordinal() >= Action.ALERT.ordinal());
    }

    /** Cash structuring from the Edge Config: the second band amount in 24 h is HELD. */
    @Test
    void cashStructuringHolds() {
        EdgeController c = controller(TypologyFixtures.config(d -> { }));
        Map<String, Object> b = base("ph-cash");
        b.put("channel", "CASH_IN");
        b.put("amount_cents", 1_300_000);
        evaluate(c, b);
        EdgeEvaluation second = evaluate(c, b);
        assertEquals(Action.HOLD, second.action());
        JsonNode record = lastRecord();
        assertEquals("HOLD", record.get("decision").asText());
        assertTrue(record.get("typologyCodes").toString().contains("CASH_STRUCTURING_24H"));
    }

    /** R-171: a spike against the card's own amounts is HELD. */
    @Test
    void zscoreSpikeHolds() {
        EdgeController c = controller(null);
        long z = rule("z-score");
        for (long cents : new long[]{1001, 1201, 801, 1001, 1001}) {
            Map<String, Object> b = base("ph-z");
            b.put("amount_cents", cents);
            assertFalse(evaluate(c, b).triggeredRuleIds().contains(z));
        }
        Map<String, Object> spike = base("ph-z");
        spike.put("amount_cents", 50_001);
        EdgeEvaluation d = evaluate(c, spike);
        assertTrue(d.triggeredRuleIds().contains(z));
        assertEquals(Action.HOLD, d.action());
    }

    /** R-14: a transaction in a listed high-risk country. */
    @Test
    void highRiskCountryAlerts() {
        EdgeController c = controller(null);
        Map<String, Object> b = base("ph-hrc");
        b.put("country_code", "ir");
        b.put("amount_cents", 1001);
        assertTrue(evaluate(c, b).triggeredRuleIds().contains(rule("High-Risk Country Transaction")));
        Map<String, Object> ke = base("ph-hrc2");
        ke.put("amount_cents", 1001);
        assertFalse(evaluate(c, ke).triggeredRuleIds().contains(rule("High-Risk Country Transaction")));
    }

    /** R-132: a blacklisted card is BLOCKed. */
    @Test
    void blacklistedCardBlocks() {
        EdgeController c = controller(null);
        Map<String, Object> b = base("pan-blocked");
        b.put("amount_cents", 1001);
        EdgeEvaluation d = evaluate(c, b);
        assertTrue(d.triggeredRuleIds().contains(rule("Variable Value Blacklist")));
        assertEquals(Action.BLOCK, d.action());
    }

    /** Edge Config limits are enforced with their action and reported as typology codes. */
    @Test
    void limitsAreEnforced() {
        EdgeController c = controller(TypologyFixtures.config(d ->
                TypologyFixtures.limit(d, 77, "CARD", "COUNT", "PT1H", null, 2, "BLOCK")));
        for (int i = 0; i < 2; i++) {
            Map<String, Object> b = base("ph-limit");
            b.put("amount_cents", 1001);
            assertFalse(evaluate(c, b).action() == Action.BLOCK);
        }
        Map<String, Object> b = base("ph-limit");
        b.put("amount_cents", 1001);
        EdgeEvaluation third = evaluate(c, b);
        assertEquals(Action.BLOCK, third.action());
        assertTrue(third.reasons().stream().anyMatch(r -> r.contains("limit 77 exceeded")));
        JsonNode record = lastRecord();
        assertEquals("BLOCK", record.get("decision").asText());
        assertTrue(record.get("typologyCodes").toString().contains("LIMIT_77"));
    }

    /** CTR: the crossing cash transaction carries ctrRequired; a missing FX rate HOLDs, flagged. */
    @Test
    void ctrRequiredReachesTheDecisionRecord() {
        EdgeController c = controller(TypologyFixtures.config(d -> { }));
        Map<String, Object> b = base("ph-ctr");
        b.put("channel", "ATM");
        b.put("currency", "KES");
        b.put("amount_cents", 100_000_000L);
        evaluate(c, b);
        assertFalse(lastRecord().get("ctrRequired").asBoolean());
        evaluate(c, b);
        JsonNode crossing = lastRecord();
        assertTrue(crossing.get("ctrRequired").asBoolean());
        assertTrue(crossing.get("typologyCodes").toString().contains("CTR_REPORTABLE"));

        Map<String, Object> gbp = base("ph-ctr-gbp");
        gbp.put("channel", "ATM");
        gbp.put("currency", "GBP");
        gbp.put("amount_cents", 5_000);
        assertEquals(Action.HOLD, evaluate(c, gbp).action());
        JsonNode fx = lastRecord();
        assertTrue(fx.get("ctrRequired").asBoolean());
        assertTrue(fx.get("typologyCodes").toString().contains("CTR_FX_UNAVAILABLE"));
    }

    /** With every derived feature in play, the decision record still carries no transaction field. */
    @Test
    void decisionRecordHasNoTransactionFields() {
        EdgeController c = controller(TypologyFixtures.config(d -> { }));
        Map<String, Object> b = base("ph-clean");
        b.put("amount_cents", 1001);
        b.put("txn_id", "clean-1");
        evaluate(c, b);
        JsonNode record = lastRecord();
        for (String forbidden : List.of("amount", "amount_cents", "currency", "country_code", "pan_hash", "features")) {
            assertFalse(record.has(forbidden), forbidden);
        }
    }
}
