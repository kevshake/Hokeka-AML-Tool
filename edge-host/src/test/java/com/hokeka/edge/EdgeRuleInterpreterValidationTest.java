package com.hokeka.edge;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code EdgeRuleInterpreter.loadBundle} rejects exactly what the Rust {@code RuleBundle} /
 * {@code Rule} / {@code Condition} structs (edge-engine/crates/rule-core/src/lib.rs) would reject
 * via serde, closing a gap found live: a bundle missing the required top-level {@code psp_id}
 * passed this validator but was rejected by the native core as malformed — same bytes, two
 * different failure behaviours depending on which evaluator happened to be active on a node.
 */
class EdgeRuleInterpreterValidationTest {

    private static long load(EdgeRuleInterpreter interp, String json) {
        return interp.loadBundle(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsABundleMissingPspId() {
        EdgeRuleInterpreter interp = new EdgeRuleInterpreter();
        String bundle = """
                {"version":1,"rules":[]}
                """;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> load(interp, bundle));
        assertTrue(e.getMessage().contains("psp_id"),
                "expected the error to name the missing field, got: " + e.getMessage());
    }

    @Test
    void rejectsARuleMissingId() {
        EdgeRuleInterpreter interp = new EdgeRuleInterpreter();
        String bundle = """
                {"version":1,"psp_id":99,"rules":[
                  {"name":"no-id","condition":{"type":"cmp","field":"mcc","op":"EQ","value":"6011"},"action":"BLOCK"}
                ]}
                """;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> load(interp, bundle));
        assertTrue(e.getMessage().contains("id"),
                "expected the error to name the missing field, got: " + e.getMessage());
    }

    @Test
    void rejectsACmpConditionMissingField() {
        EdgeRuleInterpreter interp = new EdgeRuleInterpreter();
        String bundle = """
                {"version":1,"psp_id":99,"rules":[
                  {"id":1,"name":"bad-cmp","condition":{"type":"cmp","op":"EQ","value":"6011"},"action":"BLOCK"}
                ]}
                """;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> load(interp, bundle));
        assertTrue(e.getMessage().contains("field"),
                "expected the error to name the missing 'field', got: " + e.getMessage());
    }

    @Test
    void rejectsAnUnknownComparisonOperator() {
        EdgeRuleInterpreter interp = new EdgeRuleInterpreter();
        String bundle = """
                {"version":1,"psp_id":99,"rules":[
                  {"id":1,"name":"bad-op","condition":{"type":"cmp","field":"mcc","op":"MATCHES","value":"6011"},"action":"BLOCK"}
                ]}
                """;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> load(interp, bundle));
        assertTrue(e.getMessage().contains("MATCHES"),
                "expected the error to name the bad op, got: " + e.getMessage());
    }

    @Test
    void rejectsANestedConditionMissingItsChildrenArray() {
        EdgeRuleInterpreter interp = new EdgeRuleInterpreter();
        String bundle = """
                {"version":1,"psp_id":99,"rules":[
                  {"id":1,"name":"bad-all","condition":{"type":"all"},"action":"BLOCK"}
                ]}
                """;
        assertThrows(IllegalArgumentException.class, () -> load(interp, bundle));
    }

    @Test
    void acceptsAFullyValidBundleAndEvaluatesIt() {
        EdgeRuleInterpreter interp = new EdgeRuleInterpreter();
        String bundle = """
                {"version":7,"psp_id":99,"rules":[
                  {"id":1001,"name":"high-value-mcc6011-block","description":"Block big MCC 6011",
                   "action":"BLOCK","score":90,
                   "condition":{"type":"all","all":[
                     {"type":"cmp","field":"mcc","op":"EQ","value":"6011"},
                     {"type":"cmp","field":"amount_cents","op":"GT","value":500000}
                   ]}}
                ]}
                """;
        long version = assertDoesNotThrow(() -> load(interp, bundle));
        org.junit.jupiter.api.Assertions.assertEquals(7L, version);

        EdgeRuleInterpreter.Decision d = interp.evaluate(java.util.Map.of(
                "mcc", "6011", "amount_cents", 600000));
        org.junit.jupiter.api.Assertions.assertEquals(EdgeRuleInterpreter.Action.BLOCK, d.action());
        org.junit.jupiter.api.Assertions.assertEquals(90, d.score());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(1001L), d.triggeredRuleIds());
    }

    @Test
    void aRejectedPublishNeverReplacesTheCurrentBundle() {
        EdgeRuleInterpreter interp = new EdgeRuleInterpreter();
        // Load a good bundle first.
        load(interp, """
                {"version":1,"psp_id":99,"rules":[
                  {"id":1,"name":"allow-all-with-a-rule","condition":{"type":"cmp","field":"x","op":"EQ","value":1},"action":"ALERT"}
                ]}
                """);
        // Then attempt a malformed publish (missing psp_id) — must throw and NOT swap in.
        assertThrows(IllegalArgumentException.class, () -> load(interp, """
                {"version":2,"rules":[]}
                """));
        // The original v1 bundle must still be active.
        assertTrue(interp.hasBundle());
        EdgeRuleInterpreter.Decision d = interp.evaluate(java.util.Map.of("x", 1));
        org.junit.jupiter.api.Assertions.assertEquals(EdgeRuleInterpreter.Action.ALERT, d.action());
    }
}
