package com.posgateway.aml.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.service.rules.DynamicRuleConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiRuleGeneratorService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DynamicRuleConverter converter;

    @Autowired
    public AiRuleGeneratorService(DynamicRuleConverter converter) {
        this.converter = converter;
    }

    /**
     * Simulates AI generation of a rule from a text prompt.
     * In a real implementation, this would call OpenAI/Gemini API.
     */
    public RuleDefinition generateRuleFromText(String prompt) {
        RuleDefinition rule = new RuleDefinition();
        rule.setName("AI_GENERATED_" + System.currentTimeMillis());
        rule.setDescription("Generated from prompt: " + prompt);
        
        // Mock AI Logic: Keyword matching to build JSON
        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode conditions = json.putArray("conditions");
        ArrayNode actions = json.putArray("actions");
        
        String lowerPrompt = prompt.toLowerCase();
        
        if (lowerPrompt.contains("amount") && lowerPrompt.contains("10000")) {
            ObjectNode cond = conditions.addObject();
            cond.put("field", "amount");
            cond.put("operator", "GREATER_THAN");
            cond.put("value", "10000");
        }
        
        if (lowerPrompt.contains("flag")) {
            ObjectNode act = actions.addObject();
            act.put("type", "FLAG_CASE");
        } else if (lowerPrompt.contains("block")) {
            ObjectNode act = actions.addObject();
            act.put("type", "BLOCK_TRANSACTION");
        }
        
        // Default fallback if prompt is unclear
        if (conditions.isEmpty()) {
             ObjectNode cond = conditions.addObject();
            cond.put("field", "amount");
            cond.put("operator", "GREATER_THAN");
            cond.put("value", "5000"); // Default safe fallback
        }
        if (actions.isEmpty()) {
            ObjectNode act = actions.addObject();
            act.put("type", "FLAG_CASE");
        }

        String jsonString = json.toString();
        rule.setRuleJson(jsonString);
        
        // Generate DRL
        String drl = converter.convertJsonToDrl(rule.getName(), jsonString);
        rule.setDrlContent(drl);
        
        return rule;
    }
}
