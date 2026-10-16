package com.hokeka.edge.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.JsonValidator;
import com.networknt.schema.Keyword;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationContext;
import com.networknt.schema.ValidationMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates Edge payloads against the normative JSON Schemas of {@code docs/contracts/}
 * (EDGE_DATA_RESIDENCY_CONTRACT.md §13).
 *
 * <p>The schemas are packaged from their single source into {@code classpath:contracts/} by the
 * build (see {@code pom.xml}); their {@code $id}s ({@code https://contracts.hokeka.com/edge/...})
 * are mapped onto that classpath location, so relative {@code $ref}s such as
 * {@code common.schema.json#/$defs/uuid} resolve locally and nothing is ever fetched over the
 * network. Tests can point the same mapping at the source directory instead
 * ({@link #fromDirectory}).
 *
 * <p>Two things beyond a stock draft 2020-12 validator, both required by the contract:
 * <ul>
 *   <li>{@code format} is asserted, not just annotated (the fixtures rely on it);</li>
 *   <li>the Hokeka extension keyword {@code x-hokeka-luhnReject} is enforced with
 *       {@link LuhnGuard} (§2.5, §13) — a generic validator would silently ignore it.</li>
 * </ul>
 *
 * <p>Compiled schemas are cached; instances are thread-safe and cheap to call on the evaluate path.
 */
public final class ContractSchemas {

    /** Base IRI of every contract schema {@code $id}. */
    public static final String BASE_IRI = "https://contracts.hokeka.com/edge/";

    public static final String DECISION_RECORD_V2 = "decision-record-v2.schema.json";
    public static final String DECISION_RECORD_BATCH_V2 = "decision-record-batch-v2.schema.json";
    public static final String DECISION_RECORD_ACK_V2 = "decision-record-ack-v2.schema.json";

    /** Name of the Hokeka PAN-guard extension keyword. */
    public static final String LUHN_KEYWORD = "x-hokeka-luhnReject";

    private final JsonSchemaFactory factory;
    private final SchemaValidatorsConfig config;
    private final Map<String, JsonSchema> cache = new ConcurrentHashMap<>();

    private ContractSchemas(String mappedLocation) {
        JsonMetaSchema metaSchema = JsonMetaSchema.builder(JsonMetaSchema.getV202012())
                .keyword(new LuhnRejectKeyword())
                .build();
        this.factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012, builder -> builder
                .metaSchema(metaSchema)
                .schemaMappers(mappers -> mappers.mapPrefix(BASE_IRI, mappedLocation)));
        this.config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();
    }

    /** Schemas as packaged into the application jar ({@code classpath:contracts/}). */
    public static ContractSchemas fromClasspath() {
        return new ContractSchemas("classpath:contracts/");
    }

    /**
     * Schemas read straight from a directory laid out like {@code docs/contracts/} — the tests use
     * this so they exercise the source files, not a packaged copy.
     *
     * @param contractsDir path of the {@code docs/contracts} directory
     */
    public static ContractSchemas fromDirectory(java.nio.file.Path contractsDir) {
        String uri = contractsDir.toAbsolutePath().normalize().toUri().toString();
        return new ContractSchemas(uri.endsWith("/") ? uri : uri + "/");
    }

    /**
     * Validates {@code instance} against the named schema.
     *
     * @param schemaPath path of the schema relative to {@code docs/contracts}, e.g.
     *                   {@link #DECISION_RECORD_V2} or {@code tunnel/frame.schema.json}
     * @param instance   the JSON instance
     * @return human-readable violations (instance location + message); empty when valid. Messages
     *         name locations and rules, never the offending value, so they are safe to log.
     */
    public List<String> validate(String schemaPath, JsonNode instance) {
        JsonSchema schema = cache.computeIfAbsent(schemaPath,
                p -> factory.getSchema(SchemaLocation.of(BASE_IRI + p), config));
        Set<ValidationMessage> messages = schema.validate(instance);
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(messages.size());
        for (ValidationMessage m : messages) {
            out.add(m.getInstanceLocation() + ": " + m.getType());
        }
        Collections.sort(out);
        return out;
    }

    /**
     * One schema violation, located by an RFC 6901 JSON pointer into the instance. For
     * {@code required} and {@code additionalProperties} the pointer names the missing or extra
     * member itself (e.g. {@code /params/panHash}), so it can be reported as the tunnel error
     * {@code param} (contract §7.5). Never carries the offending value.
     *
     * @param pointer JSON pointer of the violating location ({@code ""} for the root)
     * @param keyword the failing schema keyword, e.g. {@code maximum}, {@code enum}
     */
    public record Violation(String pointer, String keyword) {
    }

    /**
     * Validates {@code instance} and returns its violations as JSON pointers, sorted by pointer.
     *
     * @param schemaPath schema path relative to {@code docs/contracts}; may carry a JSON-pointer
     *                   fragment, e.g. {@code tunnel/ops/ledger.backfill.params.schema.json#/$defs/backfillRow}
     * @param instance   the JSON instance
     * @return violations; empty when valid
     */
    public List<Violation> violations(String schemaPath, JsonNode instance) {
        JsonSchema schema = cache.computeIfAbsent(schemaPath,
                p -> factory.getSchema(SchemaLocation.of(BASE_IRI + p), config));
        Set<ValidationMessage> messages = schema.validate(instance);
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<Violation> out = new ArrayList<>(messages.size());
        for (ValidationMessage m : messages) {
            String pointer = pointer(m.getInstanceLocation());
            String keyword = m.getType() == null ? "" : m.getType();
            if (("required".equals(keyword) || "additionalProperties".equals(keyword)) && m.getProperty() != null) {
                pointer = pointer + "/" + escapePointer(m.getProperty());
            }
            out.add(new Violation(pointer, keyword));
        }
        out.sort(java.util.Comparator.comparing(Violation::pointer).thenComparing(Violation::keyword));
        return out;
    }

    /** RFC 6901 pointer of a validator path ({@code $.a[0].b} → {@code /a/0/b}). */
    static String pointer(JsonNodePath path) {
        if (path == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.getNameCount(); i++) {
            Object element = path.getElement(i);
            sb.append('/').append(element instanceof Integer n ? n.toString() : escapePointer(String.valueOf(element)));
        }
        return sb.toString();
    }

    /** RFC 6901 escaping of one reference token. */
    static String escapePointer(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    /**
     * Same as {@link #validate} but returns the validator's full messages, which can quote the
     * instance value. For tests that assert the reason recorded in
     * {@code tunnel/examples/expected-failures.json}; never log these in production.
     */
    public List<String> validateVerbose(String schemaPath, JsonNode instance) {
        JsonSchema schema = cache.computeIfAbsent(schemaPath,
                p -> factory.getSchema(SchemaLocation.of(BASE_IRI + p), config));
        List<String> out = new ArrayList<>();
        for (ValidationMessage m : schema.validate(instance)) {
            out.add(m.getMessage());
        }
        Collections.sort(out);
        return out;
    }

    /**
     * {@code x-hokeka-luhnReject: true} — a string instance that {@link LuhnGuard#panLike(String)}
     * flags is invalid. Non-string instances and {@code false} values are unaffected, exactly as in
     * {@code docs/contracts/validate.py}.
     */
    static final class LuhnRejectKeyword implements Keyword {

        /** The keyword name as it appears in the schemas. */
        @Override
        public String getValue() {
            return LUHN_KEYWORD;
        }

        /** Builds the per-schema-location validator for one occurrence of the keyword. */
        @Override
        public JsonValidator newValidator(SchemaLocation schemaLocation, JsonNodePath evaluationPath,
                                          JsonNode schemaNode, JsonSchema parentSchema,
                                          ValidationContext validationContext) {
            boolean enabled = schemaNode != null && schemaNode.asBoolean(false);
            return new LuhnRejectValidator(schemaLocation, evaluationPath, enabled);
        }
    }

    /** The validator instance created by {@link LuhnRejectKeyword}. */
    static final class LuhnRejectValidator implements JsonValidator {

        private final SchemaLocation schemaLocation;
        private final JsonNodePath evaluationPath;
        private final boolean enabled;

        LuhnRejectValidator(SchemaLocation schemaLocation, JsonNodePath evaluationPath, boolean enabled) {
            this.schemaLocation = schemaLocation;
            this.evaluationPath = evaluationPath;
            this.enabled = enabled;
        }

        /** Rejects a PAN-like string; the message never repeats the value. */
        @Override
        public Set<ValidationMessage> validate(ExecutionContext executionContext, JsonNode node,
                                               JsonNode rootNode, JsonNodePath instanceLocation) {
            if (!enabled || node == null || !node.isTextual() || !LuhnGuard.panLike(node.textValue())) {
                return Collections.emptySet();
            }
            return Set.of(ValidationMessage.builder()
                    .type(LUHN_KEYWORD)
                    .code(LUHN_KEYWORD)
                    .instanceLocation(instanceLocation)
                    .schemaLocation(schemaLocation)
                    .evaluationPath(evaluationPath)
                    .message(instanceLocation + ": value looks like a PAN")
                    .build());
        }

        /** Location of the keyword in its schema. */
        @Override
        public SchemaLocation getSchemaLocation() {
            return schemaLocation;
        }

        /** Evaluation path of the keyword. */
        @Override
        public JsonNodePath getEvaluationPath() {
            return evaluationPath;
        }

        /** The keyword name. */
        @Override
        public String getKeyword() {
            return LUHN_KEYWORD;
        }
    }
}
