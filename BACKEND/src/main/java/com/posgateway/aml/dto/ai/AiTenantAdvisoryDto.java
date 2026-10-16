package com.posgateway.aml.dto.ai;

import com.posgateway.aml.entity.ai.AiDecisionAudit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PSP/bank-facing AI audit view — verdict, confidence, reasons, timestamp, advisory/applied only.
 */
public record AiTenantAdvisoryDto(
        Long id,
        String recommendation,
        Double confidence,
        List<String> reasons,
        Instant createdAt,
        boolean aiApplied,
        boolean advisoryOnly
) {
    public static AiTenantAdvisoryDto from(AiDecisionAudit audit) {
        return new AiTenantAdvisoryDto(
                audit.getId(),
                audit.getRecommendation(),
                audit.getConfidence(),
                audit.getReasons(),
                audit.getCreatedAt(),
                audit.isAiApplied(),
                true);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", id);
        dto.put("recommendation", recommendation);
        dto.put("confidence", confidence);
        dto.put("reasons", reasons);
        dto.put("createdAt", createdAt);
        dto.put("aiApplied", aiApplied);
        dto.put("advisoryOnly", advisoryOnly);
        return dto;
    }
}
