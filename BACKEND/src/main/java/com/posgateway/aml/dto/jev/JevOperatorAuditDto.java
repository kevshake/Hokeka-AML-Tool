package com.posgateway.aml.dto.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.jev.JevDecisionAudit;

import java.util.Map;

/**
 * Platform-operator audit view — full persisted audit row including model, prompt, tokens, and cost.
 */
public final class JevOperatorAuditDto {

    private JevOperatorAuditDto() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> from(JevDecisionAudit audit, ObjectMapper objectMapper) {
        return objectMapper.convertValue(audit, Map.class);
    }
}
