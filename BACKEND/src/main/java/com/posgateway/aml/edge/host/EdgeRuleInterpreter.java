package com.posgateway.aml.edge.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Edge-side reference interpreter of the Rule IR bundle — the exact semantics of the Rust
 * {@code rule-core} crate, in Java.
 *
 * <p>Two roles: (1) the dev / fail-open-safe fallback path for the edge host when the native Rust
 * library is not loaded, and (2) a cross-check oracle — the same IR + features must yield the same
 * decision here and in Rust. It is a plain class (no Spring wiring): the edge host owns its
 * lifecycle; it is intentionally NOT a bean in the control-plane application.
 *
 * <p>Pure and I/O-free: feature values are supplied by the caller (the host reads them from the
 * local Aerospike feature store before calling in), mirroring the native kernel.
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
    private volatile JsonNode bundle; // active rule bundle (atomic reference swap = hot-swap)

    /** Load/replace the active bundle from its decrypted IR JSON (post HSE-1 open). */
    public long loadBundle(byte[] bundleJson) {
        try {
            JsonNode parsed = mapper.readTree(bundleJson);
            this.bundle = parsed; // atomic publish
            return parsed.path("version").asLong(-1);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid rule bundle IR", e);
        }
    }

    public boolean hasBundle() {
        return bundle != null;
    }

    /**
     * Evaluate a transaction's features against the active bundle. Fail-closed: with no bundle
     * loaded, returns HOLD (never a silent ALLOW), matching the native engine.
     */
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
        String type = cond.path("type").asText();
        switch (type) {
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
            return false; // a missing feature never satisfies a comparison
        }
        Object actual = f.get(field);
        JsonNode expected = cond.get("value");
        String op = cond.path("op").asText();
        switch (op) {
            case "EQ": return valuesEqual(actual, expected);
            case "NE": return !valuesEqual(actual, expected);
            case "GT": case "GTE": case "LT": case "LTE": {
                Double a = asNum(actual);
                // NB: must not be a conditional expression — mixing `double` and `Double` arms
                // triggers binary numeric promotion, which unboxes a null `parseNum` result and
                // throws NPE on any non-numeric literal.
                Double e = expectedAsNum(expected);
                if (a == null || e == null) {
                    return false;
                }
                return switch (op) {
                    case "GT" -> a > e;
                    case "GTE" -> a >= e;
                    case "LT" -> a < e;
                    default -> a <= e;
                };
            }
            case "IN": {
                String a = valueToString(actual);
                String set = expected.asText();
                for (String item : set.split(",")) {
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
     * mix {@code double} and {@code Double} unboxes the {@code null} branch and throws.
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
