package com.posgateway.aml.service.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class DynamicRuleConverter {

    private static final Pattern SAFE_RULE_NAME = Pattern.compile("[A-Za-z0-9 _.,:()\\-]+");

    private static final Map<String, String> FIELD_MAP = new HashMap<>();
    static {
        FIELD_MAP.put("amount", "amount");
        FIELD_MAP.put("country_code", "countryCode");
        FIELD_MAP.put("countryCode", "countryCode");
        FIELD_MAP.put("merchant_id", "merchantId");
        FIELD_MAP.put("merchantId", "merchantId");
        FIELD_MAP.put("currency", "currency");
        FIELD_MAP.put("channel", "channel");
        FIELD_MAP.put("pan_hash", "panHash");
        FIELD_MAP.put("panHash", "panHash");
        FIELD_MAP.put("ml_score", "mlScore");
        FIELD_MAP.put("mlScore", "mlScore");
        FIELD_MAP.put("page_rank", "pageRank");
        FIELD_MAP.put("pageRank", "pageRank");
        FIELD_MAP.put("connection_count", "connectionCount");
        FIELD_MAP.put("connectionCount", "connectionCount");
        FIELD_MAP.put("pan_txn_count_1h", "panTxnCount1h");
        FIELD_MAP.put("panTxnCount1h", "panTxnCount1h");
        FIELD_MAP.put("krs", "krs");
        FIELD_MAP.put("cra", "cra");
        FIELD_MAP.put("trs", "trs");
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String convertJsonToDrl(String ruleName, String jsonRule) {
        try {
            String safeRuleName = sanitizeRuleName(ruleName);
            JsonNode root = objectMapper.readTree(jsonRule);
            String condition = buildCondition(root.path("conditions"));
            if (condition.isBlank()) {
                condition = "this != null";
            }

            StringBuilder drl = new StringBuilder();
            drl.append("package rules;\n\n");
            drl.append("import com.posgateway.aml.rules.TransactionFact;\n");
            drl.append("import java.math.BigDecimal;\n\n");
            drl.append("rule \"").append(escape(safeRuleName)).append("\"\n");
            drl.append("    when\n");
            drl.append("        $t : TransactionFact(").append(condition).append(")\n");
            drl.append("    then\n");
            appendActions(drl, safeRuleName, root.path("actions"));
            drl.append("end\n");
            return drl.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert rule JSON to DRL", e);
        }
    }

    private String buildCondition(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isArray()) {
            return joinConditions(node.elements(), " && ");
        }
        if (node.has("all")) {
            return "(" + joinConditions(node.get("all").elements(), " && ") + ")";
        }
        if (node.has("any")) {
            return "(" + joinConditions(node.get("any").elements(), " || ") + ")";
        }
        if (node.has("field")) {
            return buildLeafCondition(node);
        }
        return "";
    }

    private String joinConditions(Iterator<JsonNode> nodes, String delimiter) {
        StringBuilder result = new StringBuilder();
        while (nodes.hasNext()) {
            String next = buildCondition(nodes.next());
            if (!next.isBlank()) {
                if (result.length() > 0) {
                    result.append(delimiter);
                }
                result.append(next);
            }
        }
        return result.toString();
    }

    private String buildLeafCondition(JsonNode condition) {
        String sourceField = condition.path("field").asText();
        String factField = FIELD_MAP.get(sourceField);
        if (factField == null) {
            throw new IllegalArgumentException("Unsupported dynamic rule field: " + sourceField);
        }

        String operator = condition.path("operator").asText("EQUALS").toUpperCase();
        JsonNode value = condition.get("value");

        return switch (operator) {
            case "GREATER_THAN" -> factField + " > " + formatValue(factField, value);
            case "LESS_THAN" -> factField + " < " + formatValue(factField, value);
            case "GREATER_THAN_OR_EQUAL" -> factField + " >= " + formatValue(factField, value);
            case "LESS_THAN_OR_EQUAL" -> factField + " <= " + formatValue(factField, value);
            case "NOT_EQUALS" -> factField + " != " + formatValue(factField, value);
            case "CONTAINS" -> factField + " != null && " + factField + ".contains(" + formatValue(factField, value) + ")";
            case "IN" -> factField + " in (" + formatListValue(factField, value) + ")";
            case "NOT_IN" -> factField + " not in (" + formatListValue(factField, value) + ")";
            case "EQUALS" -> factField + " == " + formatValue(factField, value);
            default -> throw new IllegalArgumentException("Unsupported dynamic rule operator: " + operator);
        };
    }

    private String formatListValue(String factField, JsonNode value) {
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("IN/NOT_IN operators require an array value");
        }
        StringBuilder result = new StringBuilder();
        for (JsonNode item : value) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(formatValue(factField, item));
        }
        return result.toString();
    }

    private String formatValue(String factField, JsonNode value) {
        if (value == null || value.isNull()) {
            return "null";
        }
        if (Set.of("amount").contains(factField)) {
            return "new BigDecimal(\"" + escape(value.asText()) + "\")";
        }
        if (Set.of("mlScore", "pageRank", "krs", "cra", "trs").contains(factField)) {
            return value.asText();
        }
        if (Set.of("connectionCount", "panTxnCount1h").contains(factField)) {
            return value.asText() + "L";
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return "\"" + escape(value.asText()) + "\"";
    }

    private void appendActions(StringBuilder drl, String ruleName, JsonNode actions) {
        String decision = "REVIEW";
        boolean sarRequired = false;
        boolean ctrRequired = false;

        if (actions != null && actions.isArray()) {
            for (JsonNode action : actions) {
                String type = action.path("type").asText("").toUpperCase();
                switch (type) {
                    case "BLOCK_TRANSACTION", "BLOCK", "SUSPEND" -> decision = "BLOCK";
                    case "FLAG_CASE", "HOLD" -> {
                        if (!"BLOCK".equals(decision)) {
                            decision = "HOLD";
                        }
                    }
                    case "CREATE_SAR", "SAR" -> sarRequired = true;
                    case "CREATE_CTR", "CTR" -> ctrRequired = true;
                    case "ALERT", "REVIEW" -> {
                        if (!"BLOCK".equals(decision) && !"HOLD".equals(decision)) {
                            decision = "REVIEW";
                        }
                    }
                    default -> { }
                }
            }
        }

        drl.append("        $t.setDecision(\"").append(decision).append("\");\n");
        drl.append("        $t.addTriggeredRule(\"").append(escape(ruleName)).append("\");\n");
        drl.append("        $t.addReason(\"Dynamic rule triggered: ").append(escape(ruleName)).append("\");\n");
        if (sarRequired) {
            drl.append("        $t.setSarRequired(true);\n");
        }
        if (ctrRequired) {
            drl.append("        $t.setCtrRequired(true);\n");
        }
    }

    private String sanitizeRuleName(String ruleName) {
        String candidate = ruleName == null || ruleName.isBlank() ? "Dynamic Rule" : ruleName.trim();
        if (!SAFE_RULE_NAME.matcher(candidate).matches()) {
            throw new IllegalArgumentException("Rule name contains unsupported characters");
        }
        return candidate;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
