package com.hokeka.edge.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.EdgeRuleInterpreter;
import com.hokeka.edge.contract.ContractSchemas;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger row and the decision record v2 built from an evaluation: produced records validate
 * against the contract schema loaded from {@code docs/contracts}, and carry none of the forbidden
 * fields (contract §2.2, §4.1).
 */
class LedgerEvaluationsTest {

    private static final String HASH = "sha256:" + "ab".repeat(32);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractSchemas schemas =
            ContractSchemas.fromDirectory(Path.of(System.getProperty("contracts.dir", "../docs/contracts")));

    static Map<String, Object> request() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("txn_id", "AUTH-2026-10-16-000981");
        f.put("pan_hash", "9f2c1d");
        f.put("amount_cents", 125000);
        f.put("currency", "kes");
        f.put("merchant_id", "mrc_00421");
        f.put("mcc", 5411);
        f.put("country_code", "KE");
        f.put("channel", "ECOM");
        f.put("customer_id", "cust-77");
        f.put("cvv", "123");
        f.put("pan_txn_count_24h", 4L);
        return f;
    }

    private LedgerEvaluation row(EdgeRuleInterpreter.Decision decision, String evaluator, String failClosed,
                                 long version) {
        return LedgerEvaluations.fromEvaluation(UUID.randomUUID(), Instant.parse("2026-10-16T09:14:07.912Z"),
                "acme-eu", "acme-eu-1", request(), "9f2c1d", decision, version, version >= 0 ? HASH : "",
                evaluator, failClosed, 3_456);
    }

    @Test
    void ledgerRowHoldsTypedColumnsAndASanitisedFeatureBag() {
        LedgerEvaluation ev = row(new EdgeRuleInterpreter.Decision(EdgeRuleInterpreter.Action.FLAG, 45,
                List.of(1204L, 1311L, 1204L), List.of("velocity"), List.of(88012L, 88140L)), "java", null, 37);

        assertEquals("AUTH-2026-10-16-000981", ev.txnRef());
        assertEquals("9f2c1d", ev.panHash());
        assertEquals(125000L, ev.amountCents());
        assertEquals("KES", ev.currency());
        assertEquals("5411", ev.mcc());
        assertEquals("ECOM", ev.channel());
        assertEquals("ALERT", ev.decision(), "FLAG maps to the contract's ALERT");
        assertEquals(List.of(1204L, 1311L), ev.triggeredRuleIds(), "rule ids are de-duplicated");
        assertFalse(ev.features().containsKey("pan_hash"));
        assertFalse(ev.features().containsKey("cvv"));
        assertEquals("cust-77", ev.features().get("customer_id"));
        assertEquals(Instant.parse("2026-10-16T09:14:07.912Z"), ev.evaluatedAt());
    }

    @Test
    void producedDecisionRecordIsSchemaValidAndCarriesNoTransactionData() {
        LedgerEvaluation ev = row(new EdgeRuleInterpreter.Decision(EdgeRuleInterpreter.Action.BLOCK, 100,
                List.of(9001L), List.of("blocked merchant"), List.of(91002L)), "native", null, 37);
        ObjectNode record = LedgerEvaluations.decisionRecord(ev, mapper);

        assertEquals(List.of(), schemas.validate(ContractSchemas.DECISION_RECORD_V2, record));
        assertEquals("2026-10-16T09:14:07Z", record.path("evaluatedAt").asText(), "truncated to the second");
        assertEquals(3, record.path("latencyMs").asInt());
        assertEquals(HASH, record.path("bundleHash").asText());
        String json = record.toString();
        for (String forbidden : List.of("9f2c1d", "125000", "KES", "KE\"", "5411", "ECOM", "cust-77",
                "blocked merchant", "reasons", "features", "amount", "currency", "country", "pan")) {
            assertFalse(json.contains(forbidden), "decision record must not contain " + forbidden);
        }
    }

    @Test
    void failClosedRecordsAreHoldWithEvaluatorNoneAndNoBundleHashBeforeTheFirstBundle() {
        LedgerEvaluation ev = row(new EdgeRuleInterpreter.Decision(EdgeRuleInterpreter.Action.HOLD, 0, List.of(),
                List.of("edge engine has no active rule bundle (fail-closed)")), "none", "NO_BUNDLE", -1);
        ObjectNode record = LedgerEvaluations.decisionRecord(ev, mapper);

        assertEquals(List.of(), schemas.validate(ContractSchemas.DECISION_RECORD_V2, record));
        assertEquals("HOLD", record.path("decision").asText());
        assertEquals("none", record.path("evaluator").asText());
        assertEquals("NO_BUNDLE", record.path("failClosedCode").asText());
        assertFalse(record.has("bundleHash"));
        assertNull(ev.bundleHash());

        LedgerEvaluation ledgerHold = row(new EdgeRuleInterpreter.Decision(EdgeRuleInterpreter.Action.ALLOW, 0,
                List.of(), List.of()), "java", null, 37).asLedgerUnavailableHold("down");
        ObjectNode holdRecord = LedgerEvaluations.decisionRecord(ledgerHold, mapper);
        assertEquals(List.of(), schemas.validate(ContractSchemas.DECISION_RECORD_V2, holdRecord));
        assertEquals("LEDGER_UNAVAILABLE", holdRecord.path("failClosedCode").asText());
    }

    @Test
    void invalidColumnValuesAreLeftOutRatherThanBreakingTheRecord() {
        Map<String, Object> f = request();
        f.put("merchant_id", "4111111111111111");
        f.put("currency", "euro");
        f.put("mcc", "54x1");
        f.put("amount_cents", -5);
        LedgerEvaluation ev = LedgerEvaluations.fromEvaluation(UUID.randomUUID(), Instant.now(), "acme-eu",
                "acme-eu-1", f, null, new EdgeRuleInterpreter.Decision(EdgeRuleInterpreter.Action.ALLOW, 0,
                        List.of(), List.of()), 1, HASH, "java", null, 10);
        assertNull(ev.merchantId());
        assertNull(ev.currency());
        assertNull(ev.mcc());
        assertNull(ev.amountCents());
        assertFalse(ev.features().containsKey("merchant_id"), "a PAN-like value is stripped from features too");
        assertEquals(List.of(), schemas.validate(ContractSchemas.DECISION_RECORD_V2,
                LedgerEvaluations.decisionRecord(ev, mapper)));
    }

    @Test
    void txnRefChecksFollowTheContract() {
        assertEquals(LedgerEvaluations.TxnRefCheck.ABSENT, LedgerEvaluations.checkTxnRef(null));
        assertEquals(LedgerEvaluations.TxnRefCheck.VALID, LedgerEvaluations.checkTxnRef("AUTH-1"));
        assertEquals(LedgerEvaluations.TxnRefCheck.VALID, LedgerEvaluations.checkTxnRef(123456L));
        assertEquals(LedgerEvaluations.TxnRefCheck.LOOKS_LIKE_PAN,
                LedgerEvaluations.checkTxnRef("4111 1111 1111 1111"));
        assertEquals(LedgerEvaluations.TxnRefCheck.LOOKS_LIKE_PAN, LedgerEvaluations.checkTxnRef(4111111111111111L));
        assertEquals(LedgerEvaluations.TxnRefCheck.INVALID, LedgerEvaluations.checkTxnRef("has space"));
        assertEquals(LedgerEvaluations.TxnRefCheck.INVALID, LedgerEvaluations.checkTxnRef("x".repeat(129)));
        assertEquals(LedgerEvaluations.TxnRefCheck.INVALID, LedgerEvaluations.checkTxnRef(""));
        assertEquals(LedgerEvaluations.TxnRefCheck.INVALID, LedgerEvaluations.checkTxnRef(Map.of("a", 1)));
        assertTrue(LedgerEvaluations.contractDecision(EdgeRuleInterpreter.Action.SUSPEND).equals("HOLD"));
    }
}
