package com.posgateway.aml.edge.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Java interpreter of the Rule IR bundle — the same semantics as the Rust {@code rule-core} crate.
 *
 * <p>On the edge host it is the <b>fallback</b> evaluator: the container runs on it when the native
 * Rust core is not present, so the service is always functional; when {@code libedge_engine.so} is
 * loaded, the edge host's {@code EdgeEngine} routes evaluation through the native kernel for speed. Pure and
 * I/O-free — feature values are supplied by the caller.
 *
 * <p>IR version 2 (R10a) adds {@code exists}, {@code in_list} (against the bundle's {@code lists}
 * section, ASCII case-insensitive) and {@code const}. The vectors in
 * {@code edge-engine/test-vectors/rule_ir_parity.json} pin the shared semantics; the Rust crate and
 * this class both run them.
 */
public final class EdgeRuleInterpreter {

    /** Rule actions, ordered by severity. */
    public enum Action {
        ALLOW(0), ALERT(1), FLAG(2), HOLD(3), SUSPEND(4), BLOCK(5);
        final int severity;

        /** @param s severity rank */
        Action(int s) { this.severity = s; }

        /** The stronger of this action and {@code other}. */
        public Action max(Action other) { return other.severity > this.severity ? other : this; }
    }

    /** The evaluation result. */
    public record Decision(Action action, int score, List<Long> triggeredRuleIds, List<String> reasons,
                           List<Long> triggeredRuleVersionIds) {
        /** A decision without rule version ids. */
        public Decision(Action action, int score, List<Long> triggeredRuleIds, List<String> reasons) {
            this(action, score, triggeredRuleIds, reasons, List.of());
        }
    }

    /** Numeric equality tolerance, identical to the Rust core. */
    private static final double EQ_TOLERANCE = 1e-9;

    /** The only strings that count as numbers (the Rust {@code parse_num} grammar). */
    private static final Pattern DECIMAL = Pattern.compile("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?$");

    /** A verified bundle together with its list lookup sets, swapped as one reference. */
    private record Loaded(JsonNode bundle, Map<String, Set<String>> lists) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Loaded loaded;

    /**
     * Structural validation only — parses and checks the IR shape without swapping in a bundle.
     *
     * <p>Exists so the edge host's {@code EdgeEngine} can run the SAME schema check the Java fallback uses BEFORE
     * ever reaching the native core, so a bad publish gets the same specific message on every
     * node, native or fallback.
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

    /**
     * Validates and activates a bundle. A rejected bundle leaves the previous one in force.
     *
     * @return the bundle version
     */
    public long loadBundle(byte[] bundleJson) {
        JsonNode parsed;
        try {
            parsed = mapper.readTree(bundleJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid rule bundle IR: not valid JSON", e);
        }
        // Validate BEFORE swapping: a malformed-but-parseable publish would otherwise disarm the
        // edge to allow-everything (evaluation starts at ALLOW).
        validateBundle(parsed);
        this.loaded = new Loaded(parsed, indexLists(parsed.path("lists")));
        return parsed.path("version").asLong(-1);
    }

    /** IR condition node types the Rust {@code Condition} enum accepts (tag = "type"). */
    private static final Set<String> CONDITION_TYPES = Set.of("all", "any", "not", "cmp", "exists", "in_list", "const");

    /** IR comparison operators the Rust {@code Op} enum accepts. */
    private static final Set<String> COMPARISON_OPS =
            Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "CONTAINS");

    /**
     * Fail-closed structural validation of a Rule IR bundle, mirroring what the Rust structs
     * require via serde plus the Rust loader's cross-check that every {@code in_list} names a list
     * the bundle defines.
     */
    private static void validateBundle(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("rule bundle IR must be a JSON object");
        }
        if (!root.path("version").isNumber()) {
            throw new IllegalArgumentException("rule bundle IR has no numeric 'version'");
        }
        if (!root.path("psp_id").isIntegralNumber()) {
            throw new IllegalArgumentException(
                    "rule bundle IR has no integer 'psp_id' (required by the Rust RuleBundle struct)");
        }
        JsonNode irVersion = root.get("ir_version");
        if (irVersion != null && !(irVersion.isIntegralNumber() && irVersion.asLong() >= 0
                && irVersion.asLong() <= 0xFFFF_FFFFL)) {
            throw new IllegalArgumentException("rule bundle IR 'ir_version' must be a non-negative integer");
        }
        Set<String> listNames = validateLists(root.get("lists"));
        JsonNode rules = root.get("rules");
        if (rules == null || !rules.isArray()) {
            throw new IllegalArgumentException("rule bundle IR has no 'rules' array");
        }
        int index = 0;
        for (JsonNode rule : rules) {
            validateRule(rule, index, listNames);
            index++;
        }
    }

    /** Validates the optional {@code lists} object (name → array of strings) and returns its names. */
    private static Set<String> validateLists(JsonNode lists) {
        if (lists == null || lists.isNull()) {
            return Set.of();
        }
        if (!lists.isObject()) {
            throw new IllegalArgumentException("rule bundle IR 'lists' must be an object of string arrays");
        }
        Set<String> names = new HashSet<>();
        Iterator<Map.Entry<String, JsonNode>> it = lists.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!e.getValue().isArray()) {
                throw new IllegalArgumentException("rule bundle IR list '" + e.getKey() + "' is not an array");
            }
            for (JsonNode item : e.getValue()) {
                if (!item.isTextual()) {
                    throw new IllegalArgumentException("rule bundle IR list '" + e.getKey() + "' has a non-string item");
                }
            }
            names.add(e.getKey());
        }
        return names;
    }

    /** Validate one rule against the Rust {@code Rule} struct's required fields. */
    private static void validateRule(JsonNode rule, int index, Set<String> listNames) {
        String where = "rule bundle IR: rule[" + index + "]";
        if (!rule.path("id").isIntegralNumber()) {
            throw new IllegalArgumentException(where + " has no integer 'id'");
        }
        long ruleId = rule.path("id").asLong();
        where = "rule bundle IR: rule id " + ruleId;
        if (!rule.has("name") || !rule.get("name").isTextual()) {
            throw new IllegalArgumentException(where + " has no string 'name'");
        }
        JsonNode condition = rule.get("condition");
        if (condition == null || condition.isNull()) {
            throw new IllegalArgumentException(where + " has no 'condition'");
        }
        validateCondition(condition, where, listNames);

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

    /** Recursively validate a condition node against the Rust {@code Condition} enum's variants. */
    private static void validateCondition(JsonNode cond, String where, Set<String> listNames) {
        if (cond == null || cond.isNull() || !cond.isObject()) {
            throw new IllegalArgumentException(where + ": condition must be a JSON object");
        }
        String type = cond.path("type").asText("");
        if (!CONDITION_TYPES.contains(type)) {
            throw new IllegalArgumentException(where + ": condition has unknown type '" + type + "'");
        }
        switch (type) {
            case "all" -> validateConditionList(cond, "all", where, listNames);
            case "any" -> validateConditionList(cond, "any", where, listNames);
            case "not" -> {
                JsonNode inner = cond.get("not");
                if (inner == null || inner.isNull()) {
                    throw new IllegalArgumentException(where + ": 'not' condition has no 'not' child");
                }
                validateCondition(inner, where, listNames);
            }
            case "cmp" -> {
                requireField(cond, "cmp", where);
                String op = cond.path("op").asText("");
                if (!COMPARISON_OPS.contains(op)) {
                    throw new IllegalArgumentException(where + ": 'cmp' condition on field '"
                            + cond.path("field").asText() + "' has unknown op '" + op + "'");
                }
                JsonNode value = cond.get("value");
                if (value == null || !(value.isNumber() || value.isBoolean() || value.isTextual())) {
                    throw new IllegalArgumentException(where + ": 'cmp' condition on field '"
                            + cond.path("field").asText() + "' has no scalar 'value'");
                }
            }
            case "exists" -> requireField(cond, "exists", where);
            case "in_list" -> {
                requireField(cond, "in_list", where);
                if (!cond.path("list").isTextual()) {
                    throw new IllegalArgumentException(where + ": 'in_list' condition has no string 'list'");
                }
                if (!listNames.contains(cond.get("list").asText())) {
                    throw new IllegalArgumentException(where + ": 'in_list' references undefined list '"
                            + cond.get("list").asText() + "'");
                }
            }
            case "const" -> {
                if (!cond.path("value").isBoolean()) {
                    throw new IllegalArgumentException(where + ": 'const' condition has no boolean 'value'");
                }
            }
            default -> throw new IllegalStateException("unreachable: type already validated");
        }
    }

    /** Requires a string {@code field} member on a leaf condition. */
    private static void requireField(JsonNode cond, String type, String where) {
        if (!cond.has("field") || !cond.get("field").isTextual()) {
            throw new IllegalArgumentException(where + ": '" + type + "' condition has no string 'field'");
        }
    }

    /** Validates the child array of an {@code all}/{@code any} group. */
    private static void validateConditionList(JsonNode cond, String key, String where, Set<String> listNames) {
        JsonNode list = cond.get(key);
        if (list == null || !list.isArray()) {
            throw new IllegalArgumentException(where + ": '" + key + "' condition has no '" + key + "' array");
        }
        for (JsonNode child : list) {
            validateCondition(child, where, listNames);
        }
    }

    /** Builds the ASCII-uppercased lookup sets of a validated {@code lists} object. */
    private static Map<String, Set<String>> indexLists(JsonNode lists) {
        if (lists == null || !lists.isObject()) {
            return Map.of();
        }
        Map<String, Set<String>> out = new HashMap<>();
        lists.fields().forEachRemaining(e -> {
            Set<String> set = new HashSet<>();
            e.getValue().forEach(item -> set.add(asciiUpper(item.asText())));
            out.put(e.getKey(), Collections.unmodifiableSet(set));
        });
        return Collections.unmodifiableMap(out);
    }

    /** True once a bundle has been verified and loaded. */
    public boolean hasBundle() {
        return loaded != null;
    }

    /** Evaluates every rule (no short-circuit) and returns the strongest action and summed score. */
    public Decision evaluate(Map<String, Object> features) {
        Loaded current = this.loaded;
        if (current == null) {
            return new Decision(Action.HOLD, 0, List.of(),
                    List.of("edge engine has no active rule bundle (fail-closed)"));
        }
        Action action = Action.ALLOW;
        int score = 0;
        List<Long> triggered = new ArrayList<>();
        List<Long> versions = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (JsonNode rule : current.bundle().path("rules")) {
            if (evalCondition(rule.get("condition"), features, current.lists())) {
                action = action.max(Action.valueOf(rule.path("action").asText("ALLOW")));
                score = saturatingAdd(score, rule.path("score").asInt(0));
                triggered.add(rule.path("id").asLong());
                versions.add(rule.hasNonNull("rule_version_id") ? rule.path("rule_version_id").asLong() : null);
                String desc = rule.path("description").asText(null);
                reasons.add(desc != null && !desc.isEmpty() ? desc : rule.path("name").asText(""));
            }
        }
        return new Decision(action, score, triggered, reasons, versions);
    }

    /** i32 saturating addition, as the Rust core's {@code saturating_add}. */
    private static int saturatingAdd(int a, int b) {
        long sum = (long) a + b;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, sum));
    }

    /**
     * Content hash of the loaded bundle's rule-version manifest, using the same canonical form as
     * {@code EdgeRuleCompiler.manifestHash}. A declared {@code manifest_hash} is returned only when
     * it matches that form.
     */
    public String manifestHash() {
        Loaded current = this.loaded;
        if (current == null) {
            return "";
        }
        String computed = manifestHash(current.bundle().path("rules"));
        String declared = current.bundle().path("manifest_hash").asText("");
        if (!declared.isEmpty() && declared.equals(computed)) {
            return declared;
        }
        return computed;
    }

    /** Canonical manifest hash: {@code ruleId:ruleVersionId} lines sorted by rule id, SHA-256. */
    public static String manifestHash(JsonNode rules) {
        List<String> lines = new ArrayList<>();
        if (rules != null && rules.isArray()) {
            List<JsonNode> ordered = new ArrayList<>();
            rules.forEach(ordered::add);
            ordered.sort(java.util.Comparator.comparingLong(r -> r.path("id").asLong()));
            for (JsonNode rule : ordered) {
                String version = rule.hasNonNull("rule_version_id")
                        ? Long.toString(rule.path("rule_version_id").asLong()) : "";
                lines.add(rule.path("id").asLong() + ":" + version);
            }
        }
        return sha256(String.join("\n", lines));
    }

    /** {@code sha256:<hex>} of a UTF-8 string. */
    private static String sha256(String canonical) {
        try {
            byte[] dig = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "sha256:" + java.util.HexFormat.of().formatHex(dig);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Recursively evaluates one condition node; unknown types are false. */
    private boolean evalCondition(JsonNode cond, Map<String, Object> f, Map<String, Set<String>> lists) {
        if (cond == null || cond.isNull()) {
            return false;
        }
        switch (cond.path("type").asText()) {
            case "all":
                for (JsonNode c : cond.path("all")) {
                    if (!evalCondition(c, f, lists)) {
                        return false;
                    }
                }
                return true;
            case "any":
                for (JsonNode c : cond.path("any")) {
                    if (evalCondition(c, f, lists)) {
                        return true;
                    }
                }
                return false;
            case "not":
                return !evalCondition(cond.get("not"), f, lists);
            case "cmp":
                return evalCmp(cond, f);
            case "exists":
                return scalar(f, cond.path("field").asText()) != null;
            case "in_list": {
                Object actual = scalar(f, cond.path("field").asText());
                if (actual == null) {
                    return false;
                }
                Set<String> set = lists.get(cond.path("list").asText());
                return set != null && set.contains(asciiUpper(valueToString(actual)));
            }
            case "const":
                return cond.path("value").asBoolean(false);
            default:
                return false;
        }
    }

    /**
     * The feature's value when it is a scalar (number, boolean, string); {@code null} for an absent
     * key, a JSON null, a map, a list or anything else — the Rust core drops those at parse time.
     */
    private static Object scalar(Map<String, Object> f, String field) {
        Object v = f.get(field);
        if (v instanceof Number || v instanceof Boolean || v instanceof CharSequence) {
            return v instanceof CharSequence cs ? cs.toString() : v;
        }
        return null;
    }

    /** Evaluates a {@code cmp} leaf; a missing feature never satisfies a comparison. */
    private boolean evalCmp(JsonNode cond, Map<String, Object> f) {
        Object actual = scalar(f, cond.path("field").asText());
        if (actual == null) {
            return false;
        }
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
                if (!expected.isTextual()) {
                    return valuesEqual(actual, expected);
                }
                String a = valueToString(actual);
                for (String item : expected.asText().split(",", -1)) {
                    if (item.trim().equals(a)) {
                        return true;
                    }
                }
                return false;
            }
            case "CONTAINS":
                return valueToString(actual).contains(literalToString(expected));
            default:
                return false;
        }
    }

    /** Numeric equality within tolerance when both sides are numeric, else string equality. */
    private boolean valuesEqual(Object actual, JsonNode expected) {
        Double a = asNum(actual);
        Double e = expectedAsNum(expected);
        if (a != null && e != null) {
            return Math.abs(a - e) < EQ_TOLERANCE;
        }
        return valueToString(actual).equals(literalToString(expected));
    }

    /** String form of an IR literal, the same as {@link #valueToString} of its Java value. */
    private static String literalToString(JsonNode expected) {
        if (expected == null || expected.isNull()) {
            return "";
        }
        if (expected.isNumber()) {
            return valueToString(expected.doubleValue());
        }
        if (expected.isBoolean()) {
            return String.valueOf(expected.asBoolean());
        }
        return expected.asText();
    }

    /**
     * Numeric view of an IR literal, or {@code null} when it is not numeric. Written as statements
     * rather than a conditional expression on purpose: mixing {@code double} and {@code Double} arms
     * unboxes a null and throws NPE.
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

    /** Numeric view of a feature: numbers, booleans as 1/0, strictly decimal strings. */
    private static Double asNum(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        if (v instanceof String s) return parseNum(s);
        return null;
    }

    /** Parses a plain decimal literal (no whitespace, inf/NaN, hex or type suffix), finite only. */
    static Double parseNum(String s) {
        if (s == null || !DECIMAL.matcher(s).matches()) {
            return null;
        }
        try {
            double d = Double.parseDouble(s);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * String form of a value: integral numbers as a saturating 64-bit integer, other numbers in
     * shortest plain decimal form (as Rust's {@code Display}), booleans as true/false.
     */
    static String valueToString(Object v) {
        if (v instanceof Boolean b) return b.toString();
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d)) {
                return String.valueOf((long) d);
            }
            if (!Double.isFinite(d)) {
                return String.valueOf(d);
            }
            return new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(v);
    }

    /** ASCII-only upper-casing, matching Rust's {@code to_ascii_uppercase}. */
    static String asciiUpper(String s) {
        char[] out = s.toCharArray();
        for (int i = 0; i < out.length; i++) {
            char c = out[i];
            if (c >= 'a' && c <= 'z') {
                out[i] = (char) (c - 32);
            }
        }
        return new String(out);
    }
}
