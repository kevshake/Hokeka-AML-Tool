package com.posgateway.aml.service.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.EdgeBundleService;
import com.posgateway.aml.edge.EdgeBundleService.CompiledRule;
import com.posgateway.aml.entity.rules.RuleDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a PSP's authored {@link RuleDefinition} rows into the edge <b>Rule IR</b> consumed by
 * {@link EdgeBundleService} (and, at the edge, by the Rust {@code rule-core} crate and the Java
 * reference interpreter {@code EdgeRuleInterpreter}).
 *
 * <p>Rules are authored centrally as {@code rule_json} — a nested structure of {@code all}/{@code
 * any} groups over {@code {field, operator, value}} leaves, the same shape
 * {@link com.posgateway.aml.service.rules.DynamicRuleConverter} turns into DRL for the central
 * engine. This class maps that shape onto the IR's {@code all}/{@code any}/{@code not}/{@code cmp}
 * node types and onto the edge's snake_case feature names.
 *
 * <p><b>Fail-closed on unsupported rules:</b> a rule whose JSON cannot be compiled (unknown field,
 * unsupported operator, DRL-only rule with no structured JSON) is <i>omitted</i> from the bundle and
 * logged at WARN, and the caller can read the omissions from
 * {@link CompilationResult#skipped()}. It is never emitted as a permissive placeholder.
 */
@Component
public class EdgeRuleCompiler {

    private static final Logger log = LoggerFactory.getLogger(EdgeRuleCompiler.class);

    /** Authoring field name → edge feature-store field name (snake_case, as the IR uses). */
    private static final Map<String, String> FIELD_MAP = new HashMap<>();
    static {
        FIELD_MAP.put("amount", "amount");
        FIELD_MAP.put("country", "country_code");
        FIELD_MAP.put("country_code", "country_code");
        FIELD_MAP.put("countryCode", "country_code");
        FIELD_MAP.put("merchant_id", "merchant_id");
        FIELD_MAP.put("merchantId", "merchant_id");
        FIELD_MAP.put("mcc", "mcc");
        FIELD_MAP.put("merchantCategoryCode", "mcc");
        FIELD_MAP.put("merchant_category_code", "mcc");
        FIELD_MAP.put("currency", "currency");
        FIELD_MAP.put("channel", "channel");
        FIELD_MAP.put("pan_hash", "pan_hash");
        FIELD_MAP.put("panHash", "pan_hash");
        FIELD_MAP.put("ml_score", "ml_score");
        FIELD_MAP.put("mlScore", "ml_score");
        FIELD_MAP.put("page_rank", "page_rank");
        FIELD_MAP.put("pageRank", "page_rank");
        FIELD_MAP.put("connection_count", "connection_count");
        FIELD_MAP.put("connectionCount", "connection_count");
        FIELD_MAP.put("pan_txn_count_1h", "pan_txn_count_1h");
        FIELD_MAP.put("panTxnCount1h", "pan_txn_count_1h");
        FIELD_MAP.put("krs", "krs");
        FIELD_MAP.put("cra", "cra");
        FIELD_MAP.put("trs", "trs");
        FIELD_MAP.put("ip_vpn_or_proxy", "ip_vpn_or_proxy");
        FIELD_MAP.put("ipVpnOrProxy", "ip_vpn_or_proxy");
    }

    /** Actions the edge IR understands (mirrors {@code EdgeRuleInterpreter.Action}). */
    private static final Set<String> IR_ACTIONS = Set.of("ALLOW", "ALERT", "HOLD", "BLOCK");

    private final ObjectMapper mapper = new ObjectMapper();
    private final EdgeBundleService bundleService;

    public EdgeRuleCompiler(EdgeBundleService bundleService) {
        this.bundleService = bundleService;
    }

    /**
     * @param version the bundle version — monotonic in the PSP's rule-set mutation time, so an edge
     *                can compare it against its running version with a plain {@code >}
     * @param rules   the compiled IR rules, in evaluation order
     * @param skipped names of rules that could not be compiled to IR
     */
    public record CompilationResult(long version, List<CompiledRule> rules, List<String> skipped) {}

    /**
     * Compile every enabled rule visible to a PSP.
     *
     * @param ruleDefinitions the PSP's enabled rules (its own copies, or the system defaults)
     */
    public CompilationResult compile(List<RuleDefinition> ruleDefinitions) {
        List<CompiledRule> compiled = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        long version = 0L;

        for (RuleDefinition def : ruleDefinitions) {
            version = Math.max(version, mutationStamp(def));
            try {
                ObjectNode condition = compileCondition(def);
                compiled.add(new CompiledRule(
                        def.getId(),
                        def.getName(),
                        def.getDescription(),
                        condition,
                        normaliseAction(def.getAction()),
                        def.getScore() == null ? 0 : def.getScore(),
                        def.getPriority() == null ? 0 : def.getPriority()));
            } catch (RuntimeException e) {
                skipped.add(def.getName());
                log.warn("Edge bundle: rule '{}' (id {}) omitted — {}", def.getName(), def.getId(),
                        e.getMessage());
            }
        }
        // Rule count participates in the version so that deleting a rule (which lowers no
        // updated_at) still produces a version the edge sees as new.
        version = version * 1000 + Math.min(999, compiled.size());
        return new CompilationResult(version, compiled, skipped);
    }

    private long mutationStamp(RuleDefinition def) {
        LocalDateTime t = def.getUpdatedAt() != null ? def.getUpdatedAt() : def.getCreatedAt();
        return t == null ? 0L : t.toEpochSecond(ZoneOffset.UTC);
    }

    private String normaliseAction(String action) {
        if (action == null) {
            return "ALERT";
        }
        String upper = action.trim().toUpperCase();
        if (IR_ACTIONS.contains(upper)) {
            return upper;
        }
        // The central engine also emits REVIEW / FLAG; the edge's nearest equivalent is ALERT.
        return switch (upper) {
            case "REVIEW", "FLAG", "FLAG_CASE" -> "ALERT";
            case "BLOCK_TRANSACTION", "SUSPEND" -> "BLOCK";
            default -> "ALERT";
        };
    }

    private ObjectNode compileCondition(RuleDefinition def) {
        String json = def.getRuleJson();
        if (json == null || json.isBlank()) {
            return compileCatalogExpression(def);
        }
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("rule_json is not valid JSON");
        }
        JsonNode conditions = root.has("conditions") ? root.get("conditions") : root;
        ObjectNode ir = node(conditions);
        if (ir == null) {
            throw new IllegalArgumentException("rule_json has no compilable conditions");
        }
        return ir;
    }

    /** Recursively map an authoring condition node onto an IR condition node. */
    private ObjectNode node(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        if (n.isArray()) {
            return group(n.elements(), true);
        }
        if (n.has("all")) {
            return group(n.get("all").elements(), true);
        }
        if (n.has("any")) {
            return group(n.get("any").elements(), false);
        }
        if (n.has("groups") && n.get("groups").isArray()) {
            return authoringGroups(n.get("groups"));
        }
        if (n.has("not")) {
            ObjectNode inner = node(n.get("not"));
            if (inner == null) {
                throw new IllegalArgumentException("empty 'not' group");
            }
            return bundleService.not(inner);
        }
        if (n.has("field")) {
            return leaf(n);
        }
        throw new IllegalArgumentException("unrecognised condition node: " + n.toString());
    }

    private ObjectNode group(Iterator<JsonNode> children, boolean conjunction) {
        List<ObjectNode> compiled = new ArrayList<>();
        while (children.hasNext()) {
            ObjectNode child = node(children.next());
            if (child != null) {
                compiled.add(child);
            }
        }
        if (compiled.isEmpty()) {
            throw new IllegalArgumentException("empty condition group");
        }
        if (compiled.size() == 1) {
            return compiled.get(0);
        }
        ObjectNode[] arr = compiled.toArray(new ObjectNode[0]);
        return conjunction ? bundleService.all(arr) : bundleService.any(arr);
    }

    private ObjectNode leaf(JsonNode n) {
        String sourceField = n.path("field").asText();
        String field = FIELD_MAP.get(sourceField);
        if (field == null) {
            throw new IllegalArgumentException("field '" + sourceField + "' has no edge feature mapping");
        }
        String operator = canonicalOperator(n.path("operator").asText("EQUALS"));
        JsonNode value = n.get("value");
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("condition on '" + sourceField + "' has no value");
        }

        return switch (operator) {
            case "EQUALS", "EQ" -> bundleService.cmp(field, "EQ", scalar(value));
            case "NOT_EQUALS", "NE" -> bundleService.cmp(field, "NE", scalar(value));
            case "GREATER_THAN", "GT" -> bundleService.cmp(field, "GT", scalar(value));
            case "GREATER_THAN_OR_EQUAL", "GTE" -> bundleService.cmp(field, "GTE", scalar(value));
            case "LESS_THAN", "LT" -> bundleService.cmp(field, "LT", scalar(value));
            case "LESS_THAN_OR_EQUAL", "LTE" -> bundleService.cmp(field, "LTE", scalar(value));
            case "CONTAINS" -> bundleService.cmp(field, "CONTAINS", value.asText());
            case "IN" -> bundleService.cmp(field, "IN", csv(value));
            case "NOT_IN" -> bundleService.not(bundleService.cmp(field, "IN", csv(value)));
            default -> throw new IllegalArgumentException("unsupported operator '" + operator + "'");
        };
    }

    /**
     * Catalog rules (V143) are stored as SpEL plus a {@code parameters} JSON object, not as
     * {@code rule_json}. Only expressions that are a single comparison on a field the edge
     * actually has are compiled. Anything that reads {@code #features} or calls a method stays
     * omitted — dropping that clause would make the rule more permissive than the one the
     * operator approved.
     */
    private ObjectNode compileCatalogExpression(RuleDefinition def) {
        String expr = def.getRuleExpression();
        boolean methodCall = expr != null && expr.contains("#tx.is");
        if (expr == null || expr.isBlank() || expr.contains("#features") || methodCall) {
            throw new IllegalArgumentException(
                    "no structured rule_json to compile (rule_type=" + def.getRuleType() + ")");
        }
        JsonNode params = readParams(def.getParameters());
        String compact = expr.replaceAll("\\s+", "");
        if (compact.contains("#tx.amount") && compact.contains("threshold_amount") && !compact.contains("#features")) {
            double threshold = params.path("threshold_amount").asDouble(1_000_000d);
            return bundleService.cmp("amount", "GTE", threshold);
        }
        if (compact.contains("#tx.panTxnCount1h") && compact.contains("max_transactions")
                && !compact.contains("#tx.amount")) {
            double max = params.path("max_transactions").asDouble(10d);
            return bundleService.cmp("pan_txn_count_1h", "GTE", max);
        }
        throw new IllegalArgumentException(
                "SpEL expression has no edge equivalent (rule_type=" + def.getRuleType() + ")");
    }

    private JsonNode readParams(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(parameters);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    /**
     * Console visual-builder shape: {@code {"groups":[{"logic":"AND"|"OR","conditions":[leaf...]}]}}.
     * Conditions inside a group are AND. Each later group's {@code logic} joins it to the previous
     * group. Already-saved rows use this shape; new saves emit {@code conditions.all/any} directly.
     */
    private ObjectNode authoringGroups(JsonNode groups) {
        ObjectNode acc = null;
        int index = 0;
        for (JsonNode group : groups) {
            JsonNode conditions = group.get("conditions");
            if (conditions == null || !conditions.isArray()) {
                throw new IllegalArgumentException("rule group has no conditions array");
            }
            ObjectNode compiled = group(conditions.elements(), true);
            if (acc == null) {
                acc = compiled;
            } else {
                boolean or = "OR".equalsIgnoreCase(group.path("logic").asText("AND"));
                acc = or ? bundleService.any(acc, compiled) : bundleService.all(acc, compiled);
            }
            index++;
        }
        if (acc == null || index == 0) {
            throw new IllegalArgumentException("rule_json groups are empty");
        }
        return acc;
    }

    /** Accept both the canonical names and the symbols the Console visual builder emits. */
    private static String canonicalOperator(String raw) {
        String op = raw == null ? "EQUALS" : raw.trim().toUpperCase();
        return switch (op) {
            case ">=", "GTE", "GE", "GREATER_THAN_OR_EQUAL" -> "GREATER_THAN_OR_EQUAL";
            case ">", "GT", "GREATER_THAN" -> "GREATER_THAN";
            case "<=", "LTE", "LE", "LESS_THAN_OR_EQUAL" -> "LESS_THAN_OR_EQUAL";
            case "<", "LT", "LESS_THAN" -> "LESS_THAN";
            case "==", "=", "EQ", "EQUALS" -> "EQUALS";
            case "!=", "<>", "NE", "NOT_EQUALS" -> "NOT_EQUALS";
            case "CONTAINS" -> "CONTAINS";
            case "IN" -> "IN";
            case "NOT_IN", "NOT IN" -> "NOT_IN";
            default -> op;
        };
    }

    private Object scalar(JsonNode value) {
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.numberValue();
        }
        return value.asText();
    }

    /** The IR encodes set membership as a comma-separated string (see {@code EdgeRuleInterpreter}). */
    private String csv(JsonNode value) {
        if (!value.isArray()) {
            return value.asText();
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : value) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(item.asText());
        }
        if (sb.length() == 0) {
            throw new IllegalArgumentException("IN/NOT_IN requires a non-empty value list");
        }
        return sb.toString();
    }
}
