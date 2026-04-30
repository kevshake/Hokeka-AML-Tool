package com.posgateway.aml.service.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.compliance.AuditTrail;
import com.posgateway.aml.repository.AuditTrailRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditService.class);

    private final AuditTrailRepository auditTrailRepository;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;

    public AuditService(AuditTrailRepository auditTrailRepository, ObjectMapper objectMapper) {
        this.auditTrailRepository = auditTrailRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String actionType, Long merchantId, String userId, String details, Object payload) {
        log.debug("Logging audit action: {}", actionType);

        java.util.Map<String, Object> evidenceMap;
        if (payload instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> castPayload = (java.util.Map<String, Object>) payload;
            evidenceMap = castPayload;
        } else {
            // Wrap payload if not a map
            evidenceMap = java.util.Collections.singletonMap("data", payload);
        }

        AuditTrail audit = AuditTrail.builder()
                .merchantId(merchantId)
                .action(actionType)
                .performedBy(userId)
                .decisionReason(details) // Mapping details to decisionReason
                .evidence(evidenceMap)
                .build();

        auditTrailRepository.save(audit);
    }
}
