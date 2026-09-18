package com.posgateway.aml.dto.corporate;

import com.posgateway.aml.entity.corporate.AdverseMediaStatus;
import com.posgateway.aml.entity.corporate.CorporateIntelligenceStatus;
import com.posgateway.aml.entity.corporate.CorporateRegistryStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CorporateIntelligenceDtos {
    private CorporateIntelligenceDtos() {}

    public record CheckResponse(
            Long id,
            Long merchantId,
            String checkType,
            CorporateIntelligenceStatus status,
            CorporateRegistryStatus registryStatus,
            String registryProvider,
            int registryMatchScore,
            String matchedCompanyName,
            String matchedCompanyNumber,
            String matchedJurisdiction,
            String matchedCompanyStatus,
            String matchedCompanyUrl,
            List<Map<String, Object>> registryCandidates,
            Map<String, Object> registryProvenance,
            AdverseMediaStatus adverseMediaStatus,
            String adverseMediaProvider,
            String adverseMediaQuery,
            int adverseMediaArticleCount,
            List<Map<String, Object>> adverseMediaArticles,
            Map<String, Object> adverseMediaProvenance,
            int riskScore,
            String decisionReason,
            String evidenceHash,
            String checkedBy,
            LocalDateTime checkedAt,
            LocalDate retainUntil) {}
}
