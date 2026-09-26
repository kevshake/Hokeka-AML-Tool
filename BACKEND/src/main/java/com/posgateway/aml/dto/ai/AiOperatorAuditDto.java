package com.posgateway.aml.dto.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.ai.AiDecisionAudit;

import java.util.Map;

/**
 * Platform-operator audit view — full persisted audit row including model, prompt, tokens, and cost.
 */
public final class AiOperatorAuditDto {

    private AiOperatorAuditDto() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> from(AiDecisionAudit audit, ObjectMapper objectMapper) {
        return objectMapper.convertValue(audit, Map.class);
    }
}
