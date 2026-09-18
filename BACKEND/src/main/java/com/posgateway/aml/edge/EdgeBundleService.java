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
                               String action, int score, int priority) {}

    /** Build the Rule IR bundle JSON for a PSP (the plaintext that goes inside the envelope). */
    public byte[] buildBundleJson(long version, long pspId, List<CompiledRule> rules) {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", version);
        root.put("psp_id", pspId);
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
