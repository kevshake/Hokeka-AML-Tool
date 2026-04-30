package com.posgateway.aml.service.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class DynamicRuleConverter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String convertJsonToDrl(String ruleName, String jsonRule) {
        try {
            JsonNode root = objectMapper.readTree(jsonRule);
            StringBuilder drl = new StringBuilder();
            
            drl.append("package rules;\n\n");
            drl.append("import com.posgateway.aml.rules.TransactionFact;\n\n");
            
            drl.append("rule \"").append(ruleName).append("\"\n");
            drl.append("    when\n");
            drl.append("        $t : TransactionFact(");
            
            // Basic generating logic for demo purposes
            // In a real system, this would recursively parse the JSON tree
            if (root.has("conditions")) {
                JsonNode conditions = root.get("conditions");
                for (JsonNode condition : conditions) {
                    String field = condition.get("field").asText();
                    String op = condition.get("operator").asText();
                    String value = condition.get("value").asText();
                    
                    String drlOp = mapOperator(op);
                    
                    if ("amount".equals(field)) {
                         drl.append("amount ").append(drlOp).append(" new java.math.BigDecimal(\"").append(value).append("\")");
                    } else if ("country_code".equals(field)) {
                        drl.append("countryCode ").append(drlOp).append(" \"").append(value).append("\"");
                    }
                    drl.append(", ");
                }
                // Remove last comma
                 if (drl.toString().endsWith(", ")) {
                    drl.setLength(drl.length() - 2);
                }
            }
            
            drl.append(")\n");
            drl.append("    then\n");
            
            if (root.has("actions")) {
                JsonNode actions = root.get("actions");
                for (JsonNode action : actions) {
                    String type = action.get("type").asText();
                    if ("FLAG_CASE".equals(type)) {
                        drl.append("        $t.setDecision(\"HOLD\");\n");
                        drl.append("        $t.addTriggeredRule(\"").append(ruleName).append("\");\n");
                    } else if ("BLOCK_TRANSACTION".equals(type)) {
                        drl.append("        $t.setDecision(\"BLOCK\");\n");
                        drl.append("        $t.addTriggeredRule(\"").append(ruleName).append("\");\n");
                    }
                }
            }
            
            drl.append("end\n");
            
            return drl.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert rule JSON to DRL", e);
        }
    }
    
    private String mapOperator(String op) {
        switch (op) {
            case "GREATER_THAN": return ">";
            case "LESS_THAN": return "<";
            case "EQUALS": return "==";
            default: return "==";
        }
    }
}
