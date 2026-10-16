package com.hokeka.edge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the shared Rule IR v2 parity vectors ({@code edge-engine/test-vectors/rule_ir_parity.json})
 * through the Java interpreter. The Rust {@code rule-core} test {@code shared_parity_vectors} runs
 * the same file, and {@code EdgeEngineNativeIT} (under {@code -Pnative-core}) runs it through the
 * JNI build next to this interpreter, so the three agree on every operator.
 */
class EdgeRuleIrParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Location of the shared vectors, relative to the edge-host module. */
    static Path vectorsPath() {
        return Path.of(System.getProperty("parity.vectors", "../edge-engine/test-vectors/rule_ir_parity.json"));
    }

    /** The parity file parsed. */
    static JsonNode vectors() throws IOException {
        return MAPPER.readTree(Files.readAllBytes(vectorsPath()));
    }

    /** A case's features as the Java map the controller hands the engine. */
    static Map<String, Object> features(JsonNode testCase) {
        return MAPPER.convertValue(testCase.get("features"), new TypeReference<>() {
        });
    }

    /** Each case evaluates to its recorded action, score and triggered rule ids. */
    @Test
    void javaInterpreterMatchesEveryVector() throws IOException {
        JsonNode file = vectors();
        EdgeRuleInterpreter interpreter = new EdgeRuleInterpreter();
        interpreter.loadBundle(MAPPER.writeValueAsBytes(file.get("bundle")));
        assertTrue(file.get("cases").size() >= 20, "the file must exercise every operator");
        for (JsonNode c : file.get("cases")) {
            EdgeRuleInterpreter.Decision d = interpreter.evaluate(features(c));
            String name = c.get("name").asText();
            assertEquals(c.get("expect").get("action").asText(), d.action().name(), name);
            assertEquals(c.get("expect").get("score").asInt(), d.score(), name);
            List<Long> ids = new ArrayList<>();
            c.get("expect").get("triggered_rule_ids").forEach(n -> ids.add(n.asLong()));
            assertEquals(ids, d.triggeredRuleIds(), name);
        }
    }

    /** A bundle naming a list it does not define is refused, as the Rust loader refuses it. */
    @Test
    void undefinedListIsRejected() {
        String bundle = """
                {"version":1,"psp_id":1,"rules":[
                  {"id":1,"name":"x","condition":{"type":"not","not":{"type":"in_list","field":"a","list":"nope"}},
                   "action":"ALERT"}]}""";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new EdgeRuleInterpreter().loadBundle(bundle.getBytes()));
        assertTrue(e.getMessage().contains("nope"), e.getMessage());
    }

    /** Malformed v2 leaves are refused with a message naming the problem. */
    @Test
    void malformedV2LeavesAreRejected() {
        for (String cond : List.of(
                "{\"type\":\"exists\"}",
                "{\"type\":\"in_list\",\"field\":\"a\"}",
                "{\"type\":\"const\",\"value\":\"yes\"}",
                "{\"type\":\"cmp\",\"field\":\"a\",\"op\":\"EQ\",\"value\":[1]}")) {
            String bundle = "{\"version\":1,\"psp_id\":1,\"lists\":{\"l\":[\"x\"]},\"rules\":[{\"id\":1,\"name\":\"x\","
                    + "\"condition\":" + cond + ",\"action\":\"ALERT\"}]}";
            assertThrows(IllegalArgumentException.class, () -> EdgeRuleInterpreter.validate(bundle.getBytes()), cond);
        }
        String badList = "{\"version\":1,\"psp_id\":1,\"lists\":{\"l\":[1]},\"rules\":[]}";
        assertThrows(IllegalArgumentException.class, () -> EdgeRuleInterpreter.validate(badList.getBytes()));
    }

    /** The strict number grammar and text forms the two cores share. */
    @Test
    void numberGrammarAndTextForms() {
        assertEquals(12.5, EdgeRuleInterpreter.parseNum("12.5"));
        assertEquals(0.5, EdgeRuleInterpreter.parseNum(".5"));
        assertEquals(5.0, EdgeRuleInterpreter.parseNum("5."));
        for (String bad : List.of("", " 5", "5 ", "inf", "NaN", "Infinity", "0x10", "1e", "+", ".", "5d")) {
            assertEquals(null, EdgeRuleInterpreter.parseNum(bad), bad);
        }
        assertEquals("0.0000001", EdgeRuleInterpreter.valueToString(1e-7));
        assertEquals("12345678.9", EdgeRuleInterpreter.valueToString(12345678.9));
        assertEquals("5", EdgeRuleInterpreter.valueToString(5.0));
        assertEquals("STRASSE", EdgeRuleInterpreter.asciiUpper("STRAsse"));
        assertEquals("ß", EdgeRuleInterpreter.asciiUpper("ß"));
    }
}
