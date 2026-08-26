package com.hokeka.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Java interpreter of the Rule IR bundle — the same semantics as the Rust {@code rule-core} crate.
 *
 * <p>On the edge host it is the <b>fallback</b> evaluator: the container runs on it when the native
 * Rust core is not present, so the service is always functional; when {@code libedge_engine.so} is
 * loaded, {@link EdgeEngine} routes evaluation through the native kernel for speed. Pure and
 * I/O-free — feature values are supplied by the caller.
 */
public final class EdgeRuleInterpreter {

    public enum Action {
        ALLOW(0), ALERT(1), HOLD(2), BLOCK(3);
        final int severity;
        Action(int s) { this.severity = s; }
        Action max(Action other) { return other.severity > this.severity ? other : this; }
    }

    public record Decision(Action action, int score, List<Long> triggeredRuleIds, List<String> reasons) {}

    private final ObjectMapper mapper = new ObjectMapper();
    private volatile JsonNode bundle;

    /**
     * Structural validation only — parses and checks the IR shape without swapping in a bundle.
     *
     * <p>Exists so {@link EdgeEngine} can run the SAME schema check the Java fallback uses BEFORE
     * ever reaching the native core. Without this, a malformed bundle published to a node running
     * the native evaluator (the normal, healthy case) is rejected by the Rust core with a terse
     * generic message ("native core rejected the verified rule IR as malformed") with no field or
     * rule named — because native validates first and this Java-side check was only ever reached
     * afterwards, via the standby mirror. Error quality would then depend on which evaluator
     * happened to be active, which is exactly the kind of inconsistency this project does not
     * accept. Calling this first means the specific, actionable message is what a bad publish gets
     * on every node, native or fallback, always.
     *
     * @throws IllegalArgumentException with a message naming the specific field/rule that is wrong
     */
    public static void validate(byte[] bundleJson) {
        JsonNode parsed;
        try {
            parsed = new ObjectMapper().readTree(bundleJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid rule bundle IR: not valid JSON", e);
        }
        validateBundle(parsed);
    }

    public long loadBundle(byte[] bundleJson) {
        JsonNode parsed;
        try {
            parsed = mapper.readTree(bundleJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid rule bundle IR: not valid JSON", e);
        }
        // Validate the IR shape BEFORE swapping it in. The Rust loader rejects a structurally-wrong
        // bundle at parse time; the Java fallback must match, or a malformed-but-parseable publish
        // (e.g. {"version":42} with no rules array) would replace a good bundle and — because
        // evaluate() starts at ALLOW and iterates a MissingNode as empty — silently disarm the edge
        // to allow-everything. Rejecting here (before assignment) keeps the previous good bundle.
        validateBundle(parsed);
        this.bundle = parsed;
        return parsed.path("version").asLong(-1);
    }

    /** IR condition node types the Rust {@code Condition} enum accepts (tag = "type"). */
    private static final java.util.Set<String> CONDITION_TYPES =
            java.util.Set.of("all", "any", "not", "cmp");

    /** IR comparison operators the Rust {@code Op} enum accepts. */
    private static final java.util.Set<String> COMPARISON_OPS =
            java.util.Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "CONTAINS");

    /**
     * Fail-closed structural validation of a Rule IR bundle. Throws (retaining the previous
     * bundle) rather than accept anything that would evaluate to a permissive or crashing result.
     *
     * <p>Mirrors exactly what the Rust {@code RuleBundle}/{@code Rule}/{@code Condition} structs in
     * {@code edge-engine/crates/rule-core/src/lib.rs} require via serde — every field here that has
     * no {@code #[serde(default)]} in Rust is required here too. This validator only runs on the
     * FALLBACK path (native core absent or degraded): when the native core is loaded, a bundle goes
     * to the Rust deserializer first and this check never gets exercised. Without this parity, a
     * bundle malformed enough to fail Rust silently PASSED here — same bytes, different acceptance
     * behaviour depending on which evaluator happened to be active on a given node. Caught live:
     * a bundle missing the required top-level {@code psp_id} loaded cleanly through this check and
     * only failed once it reached the native core, as an opaque "malformed IR" error naming nothing.
     */
    private static void validateBundle(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("rule bundle IR must be a JSON object");
        }
        if (!root.path("version").isNumber()) {
            throw new IllegalArgumentException("rule bundle IR has no numeric 'version'");
        }
        // Required by the Rust RuleBundle struct (`psp_id: i64`, no serde default). A bundle
        // missing it deserializes fine here but is rejected by the native core as malformed —
        // this check gives that failure a name instead of an opaque native-side error.
        if (!root.path("psp_id").isIntegralNumber()) {
            throw new IllegalArgumentException(
                    "rule bundle IR has no integer 'psp_id' (required by the Rust RuleBundle struct)");
        }
        JsonNode rules = root.get("rules");
        if (rules == null || !rules.isArray()) {
            throw new IllegalArgumentException("rule bundle IR has no 'rules' array");
        }
        int index = 0;
        for (JsonNode rule : rules) {
            validateRule(rule, index);
            index++;
        }
    }

    /** Validate one rule against the Rust {@code Rule} struct's required fields. */
    private static void validateRule(JsonNode rule, int index) {
        String where = "rule bundle IR: rule[" + index + "]";

        // Rust `id: u64` — required, no default.
        if (!rule.path("id").isIntegralNumber()) {
            throw new IllegalArgumentException(where + " has no integer 'id'");
        }
        long ruleId = rule.path("id").asLong();
        where = "rule bundle IR: rule id " + ruleId;

        // Rust `name: String` — the field must be present (an empty string satisfies Rust, but a
        // missing field does not: serde has nothing to deserialize into a non-Option String).
        if (!rule.has("name") || !rule.get("name").isTextual()) {
            throw new IllegalArgumentException(where + " has no string 'name'");
        }

        JsonNode condition = rule.get("condition");
        if (condition == null || condition.isNull()) {
            throw new IllegalArgumentException(where + " has no 'condition'");
        }
        validateCondition(condition, where);

        String action = rule.path("action").asText("");
        boolean known = false;
        for (Action a : Action.values()) {
            if (a.name().equals(action)) {
                known = true;
                break;
            }
        }
        if (!known) {
            throw new IllegalArgumentException(where + ": unknown action '" + action + "'");
        }
    }

    /**
     * Recursively validate a condition node against the Rust {@code Condition} enum's per-variant
     * required fields — the same tree shape {@link #evalCondition} walks at evaluation time, but
     * checked structurally up front rather than defaulting to "no match" on a missing field.
     */
    private static void validateCondition(JsonNode cond, String where) {
        if (cond == null || cond.isNull() || !cond.isObject()) {
            throw new IllegalArgumentException(where + ": condition must be a JSON object");
        }
        String type = cond.path("type").asText("");
        if (!CONDITION_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    where + ": condition has unknown type '" + type + "'");
        }
        switch (type) {
            case "all" -> validateConditionList(cond, "all", where);
            case "any" -> validateConditionList(cond, "any", where);
            case "not" -> {
                JsonNode inner = cond.get("not");
                if (inner == null || inner.isNull()) {
                    throw new IllegalArgumentException(where + ": 'not' condition has no 'not' child");
                }
                validateCondition(inner, where);
            }
            case "cmp" -> {
                if (!cond.has("field") || !cond.get("field").isTextual()) {
                    throw new IllegalArgumentException(
                            where + ": 'cmp' condition has no string 'field'");
                }
                String op = cond.path("op").asText("");
                if (!COMPARISON_OPS.contains(op)) {
                    throw new IllegalArgumentException(
                            where + ": 'cmp' condition on field '" + cond.path("field").asText()
                                    + "' has unknown op '" + op + "'");
                }
                if (!cond.has("value") || cond.get("value").isNull()) {
                    throw new IllegalArgumentException(
                            where + ": 'cmp' condition on field '" + cond.path("field").asText()
                                    + "' has no 'value'");
                }
            }
            default -> throw new IllegalStateException("unreachable: type already validated");
        }
    }

    private static void validateConditionList(JsonNode cond, String key, String where) {
        JsonNode list = cond.get(key);
        if (list == null || !list.isArray()) {
            throw new IllegalArgumentException(where + ": '" + key + "' condition has no '" + key + "' array");
        }
        for (JsonNode child : list) {
            validateCondition(child, where);
        }
    }

    public boolean hasBundle() {
        return bundle != null;
    }

    public Decision evaluate(Map<String, Object> features) {
        JsonNode b = this.bundle;
        if (b == null) {
            return new Decision(Action.HOLD, 0, List.of(),
                    List.of("edge engine has no active rule bundle (fail-closed)"));
        }
        Action action = Action.ALLOW;
        int score = 0;
        List<Long> triggered = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (JsonNode rule : b.path("rules")) {
            if (evalCondition(rule.get("condition"), features)) {
                action = action.max(Action.valueOf(rule.path("action").asText("ALLOW")));
                score += rule.path("score").asInt(0);
                triggered.add(rule.path("id").asLong());
                String desc = rule.path("description").asText(null);
                reasons.add(desc != null && !desc.isEmpty() ? desc : rule.path("name").asText(""));
            }
        }
        return new Decision(action, score, triggered, reasons);
    }

    private boolean evalCondition(JsonNode cond, Map<String, Object> f) {
        if (cond == null || cond.isNull()) {
            return false;
        }
        switch (cond.path("type").asText()) {
            case "all":
                for (JsonNode c : cond.path("all")) {
                    if (!evalCondition(c, f)) {
                        return false;
                    }
                }
                return true;
            case "any":
                for (JsonNode c : cond.path("any")) {
                    if (evalCondition(c, f)) {
                        return true;
                    }
                }
                return false;
            case "not":
                return !evalCondition(cond.get("not"), f);
            case "cmp":
                return evalCmp(cond, f);
            default:
                return false;
        }
    }

    private boolean evalCmp(JsonNode cond, Map<String, Object> f) {
        String field = cond.path("field").asText();
        if (!f.containsKey(field) || f.get(field) == null) {
            return false;
        }
        Object actual = f.get(field);
        JsonNode expected = cond.get("value");
        switch (cond.path("op").asText()) {
            case "EQ": return valuesEqual(actual, expected);
            case "NE": return !valuesEqual(actual, expected);
            case "GT": case "GTE": case "LT": case "LTE": {
                Double a = asNum(actual);
                Double e = expectedAsNum(expected);
                if (a == null || e == null) {
                    return false;
                }
                return switch (cond.path("op").asText()) {
                    case "GT" -> a > e;
                    case "GTE" -> a >= e;
                    case "LT" -> a < e;
                    default -> a <= e;
                };
            }
            case "IN": {
                String a = valueToString(actual);
                for (String item : expected.asText().split(",")) {
                    if (item.trim().equals(a)) {
                        return true;
                    }
                }
                return false;
            }
            case "CONTAINS":
                return valueToString(actual).contains(expected.asText());
            default:
                return false;
        }
    }

    private boolean valuesEqual(Object actual, JsonNode expected) {
        Double a = asNum(actual);
        Double e = expectedAsNum(expected);
        if (a != null && e != null) {
            return Math.abs(a - e) < 1e-9;
        }
        return valueToString(actual).equals(expected.isBoolean()
                ? String.valueOf(expected.asBoolean()) : expected.asText());
    }

    /**
     * Numeric view of an IR literal, or {@code null} when it is not numeric (e.g. {@code "BRANCH"}).
     * Written as statements rather than a conditional expression on purpose: a ternary whose arms
     * mix {@code double} and {@code Double} triggers binary numeric promotion, which unboxes the
     * {@code Double} branch and throws NPE when it is null (a non-numeric string literal). That NPE
     * previously escaped as an HTTP 500 per transaction instead of a clean HOLD/false.
     */
    private static Double expectedAsNum(JsonNode expected) {
        if (expected == null || expected.isNull()) {
            return null;
        }
        if (expected.isNumber()) {
            return expected.doubleValue();
        }
        if (expected.isBoolean()) {
            return expected.asBoolean() ? 1.0 : 0.0;
        }
        return parseNum(expected.asText());
    }

    private static Double asNum(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        if (v instanceof String s) return parseNum(s);
        return null;
    }

    private static Double parseNum(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }

    private static String valueToString(Object v) {
        if (v instanceof Boolean b) return b.toString();
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
        return String.valueOf(v);
    }
}
