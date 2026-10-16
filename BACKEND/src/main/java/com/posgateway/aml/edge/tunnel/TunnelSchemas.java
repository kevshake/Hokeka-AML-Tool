package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.AbstractJsonValidator;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.JsonValidator;
import com.networknt.schema.Keyword;
import com.networknt.schema.PathType;
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
 * Validates tunnel JSON against the normative JSON Schemas (draft 2020-12) of
 * {@code docs/contracts/}. The runtime reads them from the classpath ({@code contracts/}, a copy
 * kept byte-identical to {@code docs/contracts} by {@code TunnelSchemaDriftTest}, because the
 * BACKEND Docker build context does not include {@code docs/}).
 *
 * <p>Two contract rules beyond plain JSON Schema are implemented here:
 * <ul>
 *   <li>{@code format} is <b>asserted</b>, not merely annotated, as {@code validate.py} does;</li>
 *   <li>the Hokeka extension keyword {@code x-hokeka-luhnReject} rejects PAN-like strings (§2.5).
 *       Generic validators ignore it, so §13 requires each runtime to implement it explicitly.</li>
 * </ul>
 *
 * <p>{@link Violation#message()} can quote schema constants but is never logged by the tunnel;
 * callers log only {@link Violation#pointer()} (property names, never values).
 */
public class TunnelSchemas {

    /** {@code $id} prefix of every contract schema (§12.1). */
    public static final String BASE_URI = "https://contracts.hokeka.com/edge/";

    /** Extension keyword of common.schema.json marking fields that must not hold a PAN. */
    public static final String LUHN_KEYWORD = "x-hokeka-luhnReject";

    private final JsonSchemaFactory factory;
    private final SchemaValidatorsConfig config;
    private final Map<String, JsonSchema> cache = new ConcurrentHashMap<>();

    /** One schema violation: JSON pointer of the instance location, failing keyword, message. */
    public record Violation(String pointer, String keyword, String message) {}

    /** Builds a 2020-12 factory with the Luhn keyword and the classpath mapping for the $ids. */
    public TunnelSchemas() {
        JsonMetaSchema metaSchema = JsonMetaSchema.builder(JsonMetaSchema.getV202012())
                .keyword(new LuhnRejectKeyword())
                .build();
        this.factory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012))
                .metaSchema(metaSchema)
                .schemaMappers(m -> m.mapPrefix(BASE_URI, "classpath:contracts/"))
                .build();
        this.config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .pathType(PathType.JSON_POINTER)
                .build();
    }

    /**
     * Validates {@code instance} against the schema at {@code schemaPath} (relative to
     * {@code docs/contracts}, e.g. {@code tunnel/request.schema.json}).
     *
     * @return the violations, empty when the instance is valid
     */
    public List<Violation> validate(String schemaPath, JsonNode instance) {
        JsonSchema schema = cache.computeIfAbsent(schemaPath,
                p -> factory.getSchema(SchemaLocation.of(BASE_URI + p), config));
        Set<ValidationMessage> messages = schema.validate(instance);
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Violation> out = new ArrayList<>(messages.size());
        for (ValidationMessage m : messages) {
            out.add(new Violation(pointerOf(m), m.getType(), m.getMessage()));
        }
        out.sort((a, b) -> a.pointer().compareTo(b.pointer()));
        return Collections.unmodifiableList(out);
    }

    /** True when the instance satisfies the schema. */
    public boolean isValid(String schemaPath, JsonNode instance) {
        return validate(schemaPath, instance).isEmpty();
    }

    /**
     * Pointer of the offending member: the instance location, plus the property name for
     * keywords that report on the parent object ({@code required}, {@code additionalProperties}).
     */
    private static String pointerOf(ValidationMessage m) {
        String location = m.getInstanceLocation() == null ? "" : m.getInstanceLocation().toString();
        if ("/".equals(location) || "$".equals(location)) {
            location = "";
        }
        String property = m.getProperty();
        if (property != null && !property.isEmpty()
                && ("required".equals(m.getType()) || "additionalProperties".equals(m.getType())
                    || "unevaluatedProperties".equals(m.getType()))) {
            return location + "/" + property;
        }
        return location;
    }

    /** {@code x-hokeka-luhnReject: true} — a PAN-like string fails validation (§2.5). */
    static final class LuhnRejectKeyword implements Keyword {

        /** The keyword name as it appears in the schemas. */
        @Override
        public String getValue() {
            return LUHN_KEYWORD;
        }

        /** Creates the validator for one occurrence of the keyword in a schema. */
        @Override
        public JsonValidator newValidator(SchemaLocation schemaLocation, JsonNodePath evaluationPath,
                                          JsonNode schemaNode, JsonSchema parentSchema,
                                          ValidationContext validationContext) {
            return new LuhnRejectValidator(schemaLocation, evaluationPath, this, schemaNode);
        }
    }

    /** Validator instance: flags textual instances that pass the Luhn guard. */
    static final class LuhnRejectValidator extends AbstractJsonValidator {

        private final boolean enabled;

        /** Remembers whether the keyword is set to {@code true} in the schema. */
        LuhnRejectValidator(SchemaLocation schemaLocation, JsonNodePath evaluationPath, Keyword keyword,
                            JsonNode schemaNode) {
            super(schemaLocation, evaluationPath, keyword, schemaNode);
            this.enabled = schemaNode != null && schemaNode.asBoolean(false);
        }

        /** Rejects a string that, minus spaces and hyphens, is a 13–19 digit Luhn-valid number. */
        @Override
        public Set<ValidationMessage> validate(ExecutionContext executionContext, JsonNode node,
                                               JsonNode rootNode, JsonNodePath instanceLocation) {
            if (!enabled || node == null || !node.isTextual() || !LuhnGuard.isPanLike(node.asText())) {
                return Collections.emptySet();
            }
            String where = instanceLocation == null ? "" : instanceLocation.toString();
            return Collections.singleton(ValidationMessage.builder()
                    .type(LUHN_KEYWORD)
                    .code("luhnReject")
                    .instanceLocation(instanceLocation)
                    .evaluationPath(getEvaluationPath())
                    .schemaLocation(getSchemaLocation())
                    .messageSupplier(() -> where + ": value looks like a PAN")
                    .build());
        }
    }
}
