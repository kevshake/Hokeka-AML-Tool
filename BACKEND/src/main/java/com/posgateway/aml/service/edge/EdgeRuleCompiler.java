package com.posgateway.aml.service.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.EdgeBundleService;
import com.posgateway.aml.edge.EdgeBundleService.CompiledRule;
import com.posgateway.aml.edge.EdgeBundleService.UncompiledRule;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.service.edge.typology.EdgeReferenceLists;
import com.posgateway.aml.service.edge.typology.SpelEdgeTranslator;
import com.posgateway.aml.service.edge.typology.UncompilableRuleException;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final Set<String> IR_ACTIONS = Set.of("ALLOW", "ALERT", "FLAG", "HOLD", "SUSPEND", "BLOCK");

    private final ObjectMapper mapper = new ObjectMapper();
    private final EdgeBundleService bundleService;
    private final EdgeReferenceLists referenceLists;

    /**
     * Compiler without a reference-list source: rules that need a list (high-risk countries,
     * blacklists) are reported as uncompiled with that reason. Tests and tools only.
     */
    public EdgeRuleCompiler(EdgeBundleService bundleService) {
        this(bundleService, EdgeReferenceLists.unavailable("no reference-list source is wired into this compiler"));
    }

    /**
     * @param bundleService  IR node builders
     * @param referenceLists source of the lists carried in the bundle
     */
    @Autowired
    public EdgeRuleCompiler(EdgeBundleService bundleService, EdgeReferenceLists referenceLists) {
        this.bundleService = bundleService;
        this.referenceLists = referenceLists;
    }

    /**
     * @param version    the bundle version — monotonic in the PSP's rule-set and list mutation time,
     *                   so an edge can compare it against its running version with a plain {@code >}
     * @param rules      the compiled IR rules, in evaluation order
     * @param skipped    names of rules that could not be compiled to IR
     * @param lists      bundle {@code lists} section (only lists a compiled rule reads)
     * @param uncompiled every rule left off the Edge, with the reason (bundle {@code uncompiled})
     */
    public record CompilationResult(long version, String manifestHash, List<CompiledRule> rules, List<String> skipped,
                                    Map<String, List<String>> lists, List<UncompiledRule> uncompiled) {
        /** A result with no lists and names-only omissions. */
        public CompilationResult(long version, String manifestHash, List<CompiledRule> rules, List<String> skipped) {
            this(version, manifestHash, rules, skipped, Map.of(), List.of());
        }
    }

    /**
     * Reject rule_json the Edge would skip. Throws IllegalArgumentException with the reason.
     */
    public void assertEdgeDeployable(String ruleJson) {
        RuleDefinition probe = new RuleDefinition();
        probe.setRuleJson(ruleJson);
        probe.setRuleType("JSON");
        compileCondition(probe, null, new java.util.TreeMap<>());
    }

    /** Compile for the global default rule set (no PSP-specific lists). */
    public CompilationResult compile(List<RuleDefinition> ruleDefinitions) {
        return compile(ruleDefinitions, null);
    }

    /**
     * Compile every enabled rule visible to a PSP. Catalog SpEL rules are translated with
     * {@link SpelEdgeTranslator}; a rule that cannot run on the Edge is listed in
     * {@link CompilationResult#uncompiled()} with its reason, never dropped silently and never
     * emitted as a permissive placeholder.
     *
     * @param ruleDefinitions the PSP's enabled rules (its own copies, or the system defaults)
     * @param pspId           the PSP the bundle is for (selects its blacklists and merchant lists)
     */
    public CompilationResult compile(List<RuleDefinition> ruleDefinitions, Long pspId) {
        List<CompiledRule> compiled = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<UncompiledRule> uncompiled = new ArrayList<>();
        Map<String, List<String>> lists = new java.util.TreeMap<>();
        long version = 0L;

        for (RuleDefinition def : ruleDefinitions) {
            version = Math.max(version, mutationStamp(def));
            Map<String, List<String>> ruleLists = new java.util.TreeMap<>();
            try {
                ObjectNode condition = compileCondition(def, pspId, ruleLists);
                lists.putAll(ruleLists);
                compiled.add(new CompiledRule(
                        def.getId(),
                        def.getName(),
                        def.getDescription(),
                        condition,
                        normaliseAction(def.getAction()),
                        def.getScore() == null ? 0 : def.getScore(),
                        def.getPriority() == null ? 0 : def.getPriority(),
                        def.getCurrentVersionId()));
            } catch (RuntimeException e) {
                skipped.add(def.getName());
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                uncompiled.add(new UncompiledRule(def.getId() == null ? 0L : def.getId(), def.getName(), reason));
                log.warn("Edge bundle: rule '{}' (id {}) not compiled to Edge IR — {}", def.getName(), def.getId(),
                        reason);
            }
        }
        if (!lists.isEmpty()) {
            version = Math.max(version, referenceLists.stamp(pspId));
        }
        // Rule count participates in the version so that deleting a rule (which lowers no
        // updated_at) still produces a version the edge sees as new.
        version = version * 1000 + Math.min(999, compiled.size());
        return new CompilationResult(version, manifestHash(compiled), compiled, skipped, lists, uncompiled);
    }


    /**
     * Content hash of the rule-version manifest. Canonical lines are {@code ruleId:ruleVersionId}
     * sorted by rule id, joined with a newline. The edge interpreter uses the same form.
     */
    public static String manifestHash(List<CompiledRule> rules) {
        List<CompiledRule> ordered = new ArrayList<>(rules);
        ordered.sort(java.util.Comparator.comparingLong(CompiledRule::id));
        StringBuilder canonical = new StringBuilder();
        for (CompiledRule rule : ordered) {
            if (canonical.length() > 0) {
                canonical.append('\n');
            }
            canonical.append(rule.id()).append(':');
            if (rule.ruleVersionId() != null) {
                canonical.append(rule.ruleVersionId());
            }
        }
        try {
            byte[] dig = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "sha256:" + java.util.HexFormat.of().formatHex(dig);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
        // FLAG and SUSPEND are already in IR_ACTIONS and are not rewritten.
        // REVIEW and FLAG_CASE still map to ALERT; BLOCK_TRANSACTION maps to BLOCK.
        return switch (upper) {
            case "REVIEW", "FLAG_CASE" -> "ALERT";
            case "BLOCK_TRANSACTION" -> "BLOCK";
            case "FLAG" -> "FLAG";
            case "SUSPEND" -> "SUSPEND";
            default -> "ALERT";
        };
    }

    /** The IR condition of one rule: structured rule_json, or a catalog SpEL expression. */
    private ObjectNode compileCondition(RuleDefinition def, Long pspId, Map<String, List<String>> ruleLists) {
        String json = def.getRuleJson();
        if (json == null || json.isBlank()) {
            return compileCatalogExpression(def, pspId, ruleLists);
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
        if (n.has("groups") && n.get("groups").isArray()) {
            List<ObjectNode> compiled = new ArrayList<>();
            for (JsonNode groupNode : n.get("groups")) {
                boolean and = !"OR".equalsIgnoreCase(groupNode.path("logic").asText("AND"));
                JsonNode conditions = groupNode.get("conditions");
                if (conditions == null || !conditions.isArray()) {
                    throw new IllegalArgumentException("rule group has no conditions");
                }
                compiled.add(group(conditions.elements(), and));
            }
            if (compiled.isEmpty()) {
                throw new IllegalArgumentException("rule has no groups");
            }
            if (compiled.size() == 1) {
                return compiled.get(0);
            }
            return bundleService.all(compiled.toArray(new ObjectNode[0]));
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
            // Any feature the Edge derives (velocity windows, z-score, ...) is addressable too.
            field = com.posgateway.aml.service.edge.typology.EdgeFeatureCatalog.feature(sourceField);
        }
        if (field == null) {
            throw new IllegalArgumentException("field '" + sourceField + "' has no edge feature mapping");
        }
        String operator = canonicalOperator(n.path("operator").asText("EQUALS"));
        JsonNode value = n.get("value");
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("condition on '" + sourceField + "' has no value");
        }

        operator = switch (operator) {
            case ">=" -> "GTE";
            case "<=" -> "LTE";
            case ">" -> "GT";
            case "<" -> "LT";
            case "==", "=" -> "EQ";
            case "!=" -> "NE";
            default -> operator;
        };
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
     * Catalog rules (V143 onwards) are stored as SpEL plus a {@code parameters} JSON object, not as
     * {@code rule_json}. The expression is parsed and translated by {@link SpelEdgeTranslator} with
     * the parameters folded in. DRL-only rules and expressions reading inputs the Edge does not have
     * are rejected with the reason — dropping a clause would make the rule more permissive than the
     * one the operator approved.
     */
    private ObjectNode compileCatalogExpression(RuleDefinition def, Long pspId, Map<String, List<String>> ruleLists) {
        String type = def.getRuleType() == null ? "" : def.getRuleType().trim().toUpperCase();
        String expr = def.getRuleExpression();
        if (expr == null || expr.isBlank()) {
            if ("DROOLS_DRL".equals(type) || (def.getDrlContent() != null && !def.getDrlContent().isBlank())) {
                throw new UncompilableRuleException("DRL-only rule: Drools does not run on the Edge and the rule has "
                        + "no structured rule_json or SpEL expression");
            }
            throw new UncompilableRuleException("no structured rule_json or SpEL expression to compile (rule_type="
                    + def.getRuleType() + ")");
        }
        if (!type.isEmpty() && !"SPEL".equals(type)) {
            throw new UncompilableRuleException("rule_type " + def.getRuleType() + " has no Edge form");
        }
        JsonNode params = readParams(def.getParameters());
        return new SpelEdgeTranslator(bundleService, params, pspId, referenceLists, ruleLists).translate(expr);
    }

    /** The rule's parameters object; unparseable parameters make the rule uncompilable. */
    private JsonNode readParams(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return mapper.createObjectNode();
        }
        JsonNode node;
        try {
            node = mapper.readTree(parameters);
            if (node != null && node.isTextual()) {
                node = mapper.readTree(node.textValue());
            }
        } catch (Exception e) {
            throw new UncompilableRuleException("rule parameters are not valid JSON");
        }
        if (node == null || !node.isObject()) {
            throw new UncompilableRuleException("rule parameters are not a JSON object");
        }
        return node;
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
