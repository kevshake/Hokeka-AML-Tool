package com.posgateway.aml.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.List;

/**
 * Control-plane service that compiles a PSP's rules into the edge **Rule IR bundle** and seals it
 * with {@link HokekaSecureEnvelope} (HSE-1) for delivery to that PSP's on-prem edge engine.
 *
 * <p>The emitted JSON is byte-for-byte the shape the Rust {@code rule-core} crate deserialises
 * ({@code RuleBundle}/{@code Rule}/{@code Condition}). Rules are compiled centrally and pulled by
 * the edge; the edge never authors rules. The sealed bundle is confidential end to end (under TLS)
 * so the rule logic cannot be read in transit.
 */
public class EdgeBundleService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HokekaSecureEnvelope envelope;

    public EdgeBundleService() {
        this(new HokekaSecureEnvelope());
    }

    public EdgeBundleService(HokekaSecureEnvelope envelope) {
        this.envelope = envelope;
    }

    /** A single compiled rule in edge-IR form. `condition` is an already-built IR condition tree. */
    public record CompiledRule(long id, String name, String description, ObjectNode condition,
                               String action, int score, int priority, Long ruleVersionId) {
        public CompiledRule(long id, String name, String description, ObjectNode condition,
                            String action, int score, int priority) {
            this(id, name, description, condition, action, score, priority, null);
        }
    }

    /** Build the Rule IR bundle JSON for a PSP (the plaintext that goes inside the envelope). */
    public byte[] buildBundleJson(long version, long pspId, List<CompiledRule> rules) {
        return buildBundleJson(version, pspId, rules, null);
    }

    /** Bundle JSON with a manifest hash and no lists (IR v1 shape). */
    public byte[] buildBundleJson(long version, long pspId, List<CompiledRule> rules, String manifestHash) {
        return buildBundleJson(version, pspId, rules, manifestHash, java.util.Map.of(), List.of());
    }

    /**
     * A rule that the compiler could not turn into Edge IR, listed in the bundle's
     * {@code uncompiled} manifest section with the reason (the Edge ignores the section; it exists
     * so the published bundle itself says which rules do not run there).
     *
     * @param id     rule id
     * @param name   rule name
     * @param reason why it cannot run on the Edge
     */
    public record UncompiledRule(long id, String name, String reason) {
    }

    /** IR revision emitted by {@link #buildBundleJson(long, long, List, String, java.util.Map, List)}. */
    public static final int IR_VERSION = 2;

    /**
     * Full IR v2 bundle JSON: rules, the named {@code lists} the rules' {@code in_list} conditions
     * read, and the {@code uncompiled} manifest.
     */
    public byte[] buildBundleJson(long version, long pspId, List<CompiledRule> rules, String manifestHash,
                                  java.util.Map<String, List<String>> lists, List<UncompiledRule> uncompiled) {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", version);
        root.put("psp_id", pspId);
        root.put("ir_version", IR_VERSION);
        if (manifestHash != null && !manifestHash.isBlank()) {
            root.put("manifest_hash", manifestHash);
        }
        ObjectNode listsNode = root.putObject("lists");
        new java.util.TreeMap<>(lists).forEach((name, values) -> {
            ArrayNode items = listsNode.putArray(name);
            values.forEach(items::add);
        });
        ArrayNode skipped = root.putArray("uncompiled");
        for (UncompiledRule u : uncompiled) {
            ObjectNode item = skipped.addObject();
            item.put("id", u.id());
            item.put("name", u.name());
            item.put("reason", u.reason());
        }
        ArrayNode arr = root.putArray("rules");
        for (CompiledRule r : rules) {
            ObjectNode rn = arr.addObject();
            rn.put("id", r.id());
            rn.put("name", r.name());
            if (r.description() != null) {
                rn.put("description", r.description());
            }
            rn.set("condition", r.condition());
            rn.put("action", r.action());
            rn.put("score", r.score());
            rn.put("priority", r.priority());
            if (r.ruleVersionId() != null) {
                rn.put("rule_version_id", r.ruleVersionId());
            }
        }
        try {
            return mapper.writeValueAsBytes(root);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise rule bundle", e);
        }
    }

    /** Compile + HSE-1 seal a bundle for a specific edge. */
    public byte[] compileAndSeal(long version, long pspId, List<CompiledRule> rules,
                                 byte[] edgeX25519PubRaw, PrivateKey controlPlaneEd25519Priv, String context) {
        byte[] bundleJson = buildBundleJson(version, pspId, rules);
        return envelope.seal(bundleJson, edgeX25519PubRaw, controlPlaneEd25519Priv,
                context == null ? null : context.getBytes(StandardCharsets.UTF_8));
    }

    // ── IR condition builders (mirror the Rust `Condition` enum) ─────────────────────────────────

    public ObjectNode cmp(String field, String op, Object value) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "cmp");
        n.put("field", field);
        n.put("op", op);
        if (value instanceof Boolean b) {
            n.put("value", b);
        } else if (value instanceof Number num) {
            n.put("value", num.doubleValue());
        } else {
            n.put("value", String.valueOf(value));
        }
        return n;
    }

    /** IR v2 {@code exists}: the feature is present with a scalar value. */
    public ObjectNode exists(String field) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "exists");
        n.put("field", field);
        return n;
    }

    /** IR v2 {@code in_list}: the feature's text (ASCII case-insensitive) is in a bundle list. */
    public ObjectNode inList(String field, String list) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "in_list");
        n.put("field", field);
        n.put("list", list);
        return n;
    }

    /** IR v2 {@code const}: a fixed truth value. */
    public ObjectNode constant(boolean value) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "const");
        n.put("value", value);
        return n;
    }

    /** AND group. */
    public ObjectNode all(ObjectNode... conditions) {
        return group("all", conditions);
    }

    public ObjectNode any(ObjectNode... conditions) {
        return group("any", conditions);
    }

    public ObjectNode not(ObjectNode condition) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "not");
        n.set("not", condition);
        return n;
    }

    private ObjectNode group(String type, ObjectNode... conditions) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", type);
        ArrayNode arr = n.putArray(type);
        for (ObjectNode c : conditions) {
            arr.add(c);
        }
        return n;
    }
}
