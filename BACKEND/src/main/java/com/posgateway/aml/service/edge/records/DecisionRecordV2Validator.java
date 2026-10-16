package com.posgateway.aml.service.edge.records;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict validator for {@code docs/contracts/decision-record-v2.schema.json} and the batch wrapper
 * {@code decision-record-batch-v2.schema.json} (contract section 4).
 *
 * <p>The decision record schema is <b>closed</b>: any member it does not list is a privacy
 * violation (a pan_hash, amount, currency, country, customer/device/IP id, name or free-text reason
 * slipping out of the Edge) and is answered {@code FORBIDDEN_FIELD}; the record is never stored.
 * Every other schema violation is {@code SCHEMA_INVALID}. The Luhn guard ({@code x-hokeka-luhnReject},
 * contract 2.5) is enforced on {@code txnRef} and {@code merchantId} and answers
 * {@code FORBIDDEN_FIELD} too, because the value is a probable card number.
 *
 * <p>Messages never echo a field <i>value</i>, so a rejected PAN cannot reach the acknowledgement,
 * logs or the Edge's rejection bookkeeping.
 */
public final class DecisionRecordV2Validator {

    /** Per-record rejection codes this validator produces (ack schema enum subset). */
    public enum Code { SCHEMA_INVALID, FORBIDDEN_FIELD }

    /** One validation failure. */
    public record Violation(Code code, String message) {}

    /** Members decision record v2 allows (schema {@code properties}). */
    public static final Set<String> RECORD_FIELDS = Set.of(
            "v", "edgeAssessmentId", "txnRef", "edgeNodeId", "siteId", "evaluatedAt", "decision", "score",
            "triggeredRuleIds", "ruleVersionIds", "bundleVersion", "bundleHash", "evaluator", "failClosedCode",
            "latencyMs", "merchantId", "ctrRequired", "sanctionsHit", "watchlistEntryIds", "typologyCodes");

    /** Members decision record v2 requires (schema {@code required}). */
    private static final Set<String> RECORD_REQUIRED = Set.of(
            "v", "edgeAssessmentId", "edgeNodeId", "siteId", "evaluatedAt", "decision", "score",
            "triggeredRuleIds", "ruleVersionIds", "bundleVersion", "evaluator", "latencyMs",
            "ctrRequired", "sanctionsHit");

    /** Members of the batch wrapper. */
    private static final Set<String> BATCH_FIELDS = Set.of("v", "edgeId", "siteId", "outboxSeqFrom", "outboxSeqTo", "records");

    /** Maximum records per batch (schema {@code maxItems}). */
    public static final int MAX_BATCH_RECORDS = 500;

    static final Pattern UUID = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern NODE_OR_SITE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final Pattern TXN_REF = Pattern.compile("^[\\x21-\\x7E]+$");
    private static final Pattern MERCHANT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");
    private static final Pattern INSTANT_SECONDS = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$");
    private static final Pattern BUNDLE_HASH = Pattern.compile("^sha256:[0-9a-f]{64}$");
    private static final Pattern WATCHLIST_ENTRY = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}:[A-Za-z0-9][A-Za-z0-9._/-]{0,95}$");
    private static final Pattern TYPOLOGY = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
    /** Field names safe to echo in a message (never a value, never something PAN-like). */
    private static final Pattern ECHOABLE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");

    private static final Set<String> DECISIONS = Set.of("ALLOW", "ALERT", "HOLD", "BLOCK");
    private static final Set<String> EVALUATORS_RULE = Set.of("native", "java");
    private static final Set<String> FAIL_CLOSED_CODES = Set.of(
            "NODE_NOT_AUTHORIZED", "NO_BUNDLE", "FEATURE_STORE_UNAVAILABLE", "LEDGER_UNAVAILABLE", "EVALUATOR_FAULT");

    private DecisionRecordV2Validator() {
    }

    /**
     * Validates the batch wrapper only (records' contents are validated one by one with
     * {@link #validateRecord}). A failure here means the body is not a v2 batch (HTTP 400).
     *
     * @return a description of the first problem, or empty when the wrapper is valid
     */
    public static Optional<String> validateBatch(JsonNode batch) {
        if (batch == null || !batch.isObject()) {
            return Optional.of("batch must be a JSON object");
        }
        Iterator<String> names = batch.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!BATCH_FIELDS.contains(name)) {
                return Optional.of("batch member " + echo(name) + " is not allowed");
            }
        }
        if (!isIntegerEqual(batch.get("v"), 2)) {
            return Optional.of("batch v must be 2");
        }
        if (!matches(batch.get("edgeId"), NODE_OR_SITE)) {
            return Optional.of("batch edgeId is missing or malformed");
        }
        if (!matches(batch.get("siteId"), NODE_OR_SITE)) {
            return Optional.of("batch siteId is missing or malformed");
        }
        for (String seq : new String[]{"outboxSeqFrom", "outboxSeqTo"}) {
            if (batch.has(seq) && !isIntegerInRange(batch.get(seq), 0, Long.MAX_VALUE)) {
                return Optional.of("batch " + seq + " must be a non-negative integer");
            }
        }
        JsonNode records = batch.get("records");
        if (records == null || !records.isArray() || records.isEmpty()) {
            return Optional.of("batch records must be a non-empty array");
        }
        if (records.size() > MAX_BATCH_RECORDS) {
            return Optional.of("batch carries more than " + MAX_BATCH_RECORDS + " records");
        }
        return Optional.empty();
    }

    /**
     * Validates one decision record v2.
     *
     * @return the first violation, or empty when the record conforms
     */
    public static Optional<Violation> validateRecord(JsonNode r) {
        if (r == null || !r.isObject()) {
            return schema("record must be a JSON object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = r.fields();
        while (fields.hasNext()) {
            String name = fields.next().getKey();
            if (!RECORD_FIELDS.contains(name)) {
                return forbidden("member " + echo(name) + " is not allowed in a decision record");
            }
        }
        for (String required : RECORD_REQUIRED) {
            if (!r.has(required) || r.get(required).isNull()) {
                return schema("missing required member '" + required + "'");
            }
        }
        if (!isIntegerEqual(r.get("v"), 2)) {
            return schema("v must be 2");
        }
        if (!matches(r.get("edgeAssessmentId"), UUID)) {
            return schema("edgeAssessmentId must be a lowercase UUID");
        }
        if (r.has("txnRef")) {
            JsonNode ref = r.get("txnRef");
            if (!ref.isTextual() || ref.asText().isEmpty() || ref.asText().length() > 128
                    || !TXN_REF.matcher(ref.asText()).matches()) {
                return schema("txnRef must be 1..128 printable ASCII characters without spaces");
            }
            if (looksLikePan(ref.asText())) {
                return forbidden("txnRef looks like a card number");
            }
        }
        if (!matches(r.get("edgeNodeId"), NODE_OR_SITE)) {
            return schema("edgeNodeId is malformed");
        }
        if (!matches(r.get("siteId"), NODE_OR_SITE)) {
            return schema("siteId is malformed");
        }
        JsonNode evaluatedAt = r.get("evaluatedAt");
        if (!matches(evaluatedAt, INSTANT_SECONDS) || !parsesAsInstant(evaluatedAt.asText())) {
            return schema("evaluatedAt must be an RFC 3339 UTC instant truncated to the second");
        }
        if (!r.get("decision").isTextual() || !DECISIONS.contains(r.get("decision").asText())) {
            return schema("decision must be ALLOW, ALERT, HOLD or BLOCK");
        }
        if (!isIntegerInRange(r.get("score"), 0, Long.MAX_VALUE)) {
            return schema("score must be a non-negative integer");
        }
        for (String list : new String[]{"triggeredRuleIds", "ruleVersionIds"}) {
            Optional<String> problem = ruleIdList(r.get(list));
            if (problem.isPresent()) {
                return schema(list + " " + problem.get());
            }
        }
        if (!isIntegerInRange(r.get("bundleVersion"), -1, Long.MAX_VALUE)) {
            return schema("bundleVersion must be an integer >= -1");
        }
        if (r.has("bundleHash") && !matches(r.get("bundleHash"), BUNDLE_HASH)) {
            return schema("bundleHash must be sha256:<64 hex>");
        }
        JsonNode evaluator = r.get("evaluator");
        if (!evaluator.isTextual() || !(EVALUATORS_RULE.contains(evaluator.asText()) || "none".equals(evaluator.asText()))) {
            return schema("evaluator must be native, java or none");
        }
        if (r.has("failClosedCode")) {
            JsonNode code = r.get("failClosedCode");
            if (!code.isTextual() || !FAIL_CLOSED_CODES.contains(code.asText())) {
                return schema("failClosedCode is not a known fail-closed code");
            }
        }
        if (!isIntegerInRange(r.get("latencyMs"), 0, 600_000)) {
            return schema("latencyMs must be an integer between 0 and 600000");
        }
        if (r.has("merchantId")) {
            JsonNode merchant = r.get("merchantId");
            if (!matches(merchant, MERCHANT)) {
                return schema("merchantId is malformed");
            }
            if (looksLikePan(merchant.asText())) {
                return forbidden("merchantId looks like a card number");
            }
        }
        if (!r.get("ctrRequired").isBoolean()) {
            return schema("ctrRequired must be a boolean");
        }
        if (!r.get("sanctionsHit").isBoolean()) {
            return schema("sanctionsHit must be a boolean");
        }
        if (r.has("watchlistEntryIds")) {
            Optional<String> problem = stringList(r.get("watchlistEntryIds"), WATCHLIST_ENTRY);
            if (problem.isPresent()) {
                return schema("watchlistEntryIds " + problem.get());
            }
        }
        if (r.has("typologyCodes")) {
            Optional<String> problem = stringList(r.get("typologyCodes"), TYPOLOGY);
            if (problem.isPresent()) {
                return schema("typologyCodes " + problem.get());
            }
        }
        return conditionals(r);
    }

    /** The schema's three if/then/else rules. */
    private static Optional<Violation> conditionals(JsonNode r) {
        boolean sanctionsHit = r.get("sanctionsHit").asBoolean();
        int watchlistCount = r.has("watchlistEntryIds") ? r.get("watchlistEntryIds").size() : 0;
        if (sanctionsHit && !r.has("watchlistEntryIds")) {
            return schema("missing required member 'watchlistEntryIds' for a sanctions hit");
        }
        if (sanctionsHit && watchlistCount < 1) {
            return schema("a sanctions hit must name at least one watchlist entry");
        }
        if (!sanctionsHit && watchlistCount > 0) {
            return schema("watchlistEntryIds must be empty when sanctionsHit is false");
        }
        String evaluator = r.get("evaluator").asText();
        if (r.has("failClosedCode")) {
            if (!"HOLD".equals(r.get("decision").asText())) {
                return schema("a fail-closed record must be a HOLD");
            }
            if (!"none".equals(evaluator)) {
                return schema("a fail-closed record must have evaluator none");
            }
            if (r.get("triggeredRuleIds").size() > 0) {
                return schema("a fail-closed record cannot have triggered rules");
            }
        } else if (!EVALUATORS_RULE.contains(evaluator)) {
            return schema("evaluator must be native or java unless failClosedCode is present");
        }
        boolean noBundle = r.get("bundleVersion").asLong() == -1L;
        if (noBundle && r.has("bundleHash")) {
            return schema("bundleHash must be absent when bundleVersion is -1");
        }
        if (!noBundle && !r.has("bundleHash")) {
            return schema("missing required member 'bundleHash' for bundleVersion >= 0");
        }
        return Optional.empty();
    }

    /**
     * Luhn guard (contract 2.5): after removing spaces and hyphens, 13..19 decimal digits that pass
     * the Luhn check.
     */
    public static boolean looksLikePan(String value) {
        if (value == null) {
            return false;
        }
        String digits = value.replace(" ", "").replace("-", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int d = c - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** Unique positive integers, at most 512 (common ruleIdList). */
    private static Optional<String> ruleIdList(JsonNode list) {
        if (list == null || !list.isArray()) {
            return Optional.of("must be an array");
        }
        if (list.size() > 512) {
            return Optional.of("has more than 512 items");
        }
        Set<Long> seen = new HashSet<>();
        for (JsonNode item : list) {
            if (!isIntegerInRange(item, 1, Long.MAX_VALUE)) {
                return Optional.of("items must be positive integers");
            }
            if (!seen.add(item.decimalValue().longValue())) {
                return Optional.of("items must be unique");
            }
        }
        return Optional.empty();
    }

    /** Unique strings matching {@code pattern}, at most 64. */
    private static Optional<String> stringList(JsonNode list, Pattern pattern) {
        if (!list.isArray()) {
            return Optional.of("must be an array");
        }
        if (list.size() > 64) {
            return Optional.of("has more than 64 items");
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode item : list) {
            if (!matches(item, pattern)) {
                return Optional.of("has a malformed item");
            }
            if (!seen.add(item.asText())) {
                return Optional.of("items must be unique");
            }
        }
        return Optional.empty();
    }

    /** True when {@code node} is a string fully matching {@code pattern}. */
    private static boolean matches(JsonNode node, Pattern pattern) {
        return node != null && node.isTextual() && pattern.matcher(node.asText()).matches();
    }

    /** JSON Schema "integer": a number with no fractional part (3 and 3.0 both qualify). */
    private static boolean isInteger(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return false;
        }
        if (node.isIntegralNumber()) {
            return true;
        }
        BigDecimal value = node.decimalValue();
        return value.signum() == 0 || value.stripTrailingZeros().scale() <= 0;
    }

    /** Integer within [min, max]. */
    private static boolean isIntegerInRange(JsonNode node, long min, long max) {
        if (!isInteger(node)) {
            return false;
        }
        BigDecimal value = node.decimalValue();
        return value.compareTo(BigDecimal.valueOf(min)) >= 0 && value.compareTo(BigDecimal.valueOf(max)) <= 0;
    }

    /** Integer equal to {@code expected}. */
    private static boolean isIntegerEqual(JsonNode node, long expected) {
        return isIntegerInRange(node, expected, expected);
    }

    /** True when the RFC 3339 text is a real instant (rejects e.g. month 13). */
    private static boolean parsesAsInstant(String text) {
        try {
            Instant.parse(text);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** Quotes a member name for a message only when it is a plain identifier; otherwise hides it. */
    private static String echo(String name) {
        return ECHOABLE_NAME.matcher(name).matches() && !looksLikePan(name) ? "'" + name + "'" : "(unnamed)";
    }

    private static Optional<Violation> schema(String message) {
        return Optional.of(new Violation(Code.SCHEMA_INVALID, message));
    }

    private static Optional<Violation> forbidden(String message) {
        return Optional.of(new Violation(Code.FORBIDDEN_FIELD, message));
    }
}
