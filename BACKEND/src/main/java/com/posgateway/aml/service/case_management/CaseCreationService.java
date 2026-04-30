package com.posgateway.aml.service.case_management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.CaseAlert;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for creating cases from various triggers (Rules, ML,
 * Sanctions).
 * Implements the "Alert -> Case" lifecycle transition.
 */
@Service
public class CaseCreationService {

    private static final Logger logger = LoggerFactory.getLogger(CaseCreationService.class);

    private final ComplianceCaseRepository complianceCaseRepository;
    private final ObjectMapper objectMapper;
    private final com.posgateway.aml.service.kafka.CaseEventProducer caseEventProducer;
    private final CaseEnrichmentService enrichmentService;

    @Autowired
    public CaseCreationService(ComplianceCaseRepository complianceCaseRepository,
                               ObjectMapper objectMapper,
                               @Nullable com.posgateway.aml.service.kafka.CaseEventProducer caseEventProducer,
                               CaseEnrichmentService enrichmentService) {
        this.complianceCaseRepository = complianceCaseRepository;
        this.objectMapper = objectMapper;
        this.caseEventProducer = caseEventProducer;
        this.enrichmentService = enrichmentService;
    }

    /**
     * Trigger a case from a Rule Violation.
     */
    /**
     * Trigger a case from a Rule Violation.
     */
    @Transactional
    public void triggerCaseFromRule(TransactionEntity tx, String ruleName, String ruleDescription) {
        Long merchantIdLong = parseMerchantId(tx.getMerchantId());
        // Simple rule versioning placeholder
        String ruleVersion = "v1.0";
        createOrUpdateCase(merchantIdLong, tx.getPspId(), "RULE_VIOLATION", ruleName, null,
                null, ruleVersion, 1.0, ruleDescription, tx);
    }

    /**
     * Trigger a case from High ML Risk Score.
     */
    @Transactional
    public void triggerCaseFromML(TransactionEntity tx, Double score, String modelVersion) {
        Long merchantIdLong = parseMerchantId(tx.getMerchantId());
        createOrUpdateCase(merchantIdLong, tx.getPspId(), "ML_RISK", "High ML Score", null,
                modelVersion, null, score, "ML Score: " + score, tx);
    }

    /**
     * Trigger a case from Sanctions Hit (Transaction context).
     */
    @Transactional
    public void triggerCaseFromSanctions(TransactionEntity tx, String listName, String matchDetails) {
        if (tx != null) {
            Long merchantIdLong = parseMerchantId(tx.getMerchantId());
            createOrUpdateCase(merchantIdLong, tx.getPspId(), "SANCTIONS_HIT", listName, null,
                    null, "Global-Sanctions-List", 1.0, matchDetails, tx);
        }
    }

    /**
     * Parse merchant ID from String to Long
     */
    private Long parseMerchantId(String merchantId) {
        if (merchantId == null || merchantId.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(merchantId);
        } catch (NumberFormatException e) {
            logger.warn("Invalid merchant ID format: {}", merchantId);
            return null;
        }
    }

    /**
     * Trigger a case from Sanctions Hit (Merchant/Entity context - No Transaction).
     */
    @Transactional
    public void triggerCaseFromSanctions(Long merchantId, Long pspId, String listName, String matchDetails) {
        createOrUpdateCase(merchantId, pspId, "SANCTIONS_HIT", listName, null,
                null, "Global-Sanctions-List", 1.0, matchDetails, null);
    }

    /**
     * Trigger a case from Graph Anomaly (Cycle/Mule).
     */
    @Transactional
    public void triggerCaseFromGraph(Long merchantId, Long pspId, String anomalyType, String description) {
        // Graph anomalies are high risk, so we might assign a high score or specific
        // priority
        Double anomalyScore = 0.95;
        createOrUpdateCase(merchantId, pspId, "GRAPH_ANOMALY", anomalyType, null,
                "Neo4j-GDS-v1", null, anomalyScore, description, null);
    }

    private void createOrUpdateCase(Long merchantId, Long pspId, String alertType, String ruleName, String ruleId,
            String modelVersion, String ruleVersion, Double score, String description, TransactionEntity tx) {
        // Use merchantId directly

        // ... existing case lookup logic ...
        List<ComplianceCase> existingCases = complianceCaseRepository.findByMerchantId(merchantId);
        Optional<ComplianceCase> openCaseOpt = existingCases.stream()
                .filter(c -> c.getStatus() != CaseStatus.CLOSED_CLEARED &&
                        c.getStatus() != CaseStatus.CLOSED_SAR_FILED &&
                        c.getStatus() != CaseStatus.CLOSED_BLOCKED)
                .findFirst();

        ComplianceCase cCase;
        if (openCaseOpt.isPresent()) {
            cCase = openCaseOpt.get();
            logger.info("Appending alert {} to existing case {}", alertType, cCase.getCaseReference());
        } else {
            cCase = new ComplianceCase();
            cCase.setCaseReference(generateCaseReference());
            cCase.setMerchantId(merchantId);
            cCase.setPspId(pspId != null ? pspId : (tx != null ? tx.getPspId() : null)); // Use passed ID or fallback
            cCase.setStatus(CaseStatus.NEW);
            cCase.setPriority(CasePriority.MEDIUM);
            cCase.setCreatedAt(LocalDateTime.now());
            cCase.setDescription("Auto-generated case from " + alertType);
            cCase.setAlerts(new ArrayList<>());
            logger.info("Creating new case {} for alert {}", cCase.getCaseReference(), alertType);
        }

        // 2. Create Alert
        CaseAlert alert = new CaseAlert();
        alert.setComplianceCase(cCase);
        alert.setAlertType(alertType);
        alert.setRuleName(ruleName);
        alert.setRuleId(ruleId);
        alert.setModelVersion(modelVersion);
        alert.setRuleVersion(ruleVersion);
        alert.setScore(score);
        alert.setDescription(description);
        alert.setTriggeredAt(LocalDateTime.now());

        try {
            if (tx != null) {
                alert.setRawData(objectMapper.writeValueAsString(tx));
            } else {
                alert.setRawData("{\"source\": \"PERIODIC_SCREENING\", \"merchantId\": " + merchantId + "}");
            }
        } catch (Exception e) {
            logger.error("Failed to serialize transaction data for alert", e);
            alert.setRawData("{\"error\": \"serialization_failed\"}");
        }

        // 3. Add alert to case
        if (cCase.getAlerts() == null) {
            cCase.setAlerts(new ArrayList<>());
        }
        cCase.getAlerts().add(alert);

        // 4. Update Priority if needed
        if ("SANCTIONS_HIT".equals(alertType) || (score != null && score > 0.9)) {
            cCase.setPriority(CasePriority.HIGH);
            cCase.setEscalated(true);
            cCase.setEscalationReason("Critical Alert: " + alertType);
        }

        if (cCase.getNotes() == null) {
            cCase.setNotes(new ArrayList<>());
        }

        cCase.setUpdatedAt(LocalDateTime.now());
        complianceCaseRepository.save(cCase);

        // 5. Enrich Case (Async link creation)
        try {
            if (tx != null) {
                enrichmentService.enrichWithTransaction(cCase, tx, "TRIGGERING_TRANSACTION");
            }
            if (merchantId != null) {
                enrichmentService.enrichWithMerchantProfile(cCase, merchantId);
            }
            // If risk details are available (serialized in Alert or passed differently), we
            // could enrich.
            // For now, Alert contains the main summary.
        } catch (Exception e) {
            logger.warn("Failed to enrich case {} with context", cCase.getCaseReference(), e);
        }

        // 6. Publish Kafka Event (only if producer is configured/enabled)
        if (caseEventProducer != null) {
            try {
                String eventType = (cCase.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(5)))
                        ? "CASE_CREATED"
                        : "CASE_UPDATED";
                caseEventProducer.publishCaseLifecycleEvent(cCase, eventType);
            } catch (Exception e) {
                logger.error("Failed to publish case event", e);
            }
        } else {
            logger.debug("Kafka disabled or CaseEventProducer not configured - skipping case lifecycle event for {}",
                    cCase.getCaseReference());
        }
    }

    private String generateCaseReference() {
        return "CASE-" + System.currentTimeMillis(); // Simple ID generation for now
    }
}
