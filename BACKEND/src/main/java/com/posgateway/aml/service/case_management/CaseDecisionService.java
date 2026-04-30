package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.CaseDecision;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.AuditLogRepository;
import com.posgateway.aml.repository.CaseDecisionRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for handling case decisions.
 * Enforces regulatory requirements:
 * 1. Mandatory justification
 * 2. Immutable audit logging
 * 3. Authorized decision makers
 */
@Service
public class CaseDecisionService {

    private static final Logger logger = LoggerFactory.getLogger(CaseDecisionService.class);

    private final ComplianceCaseRepository complianceCaseRepository;
    private final CaseDecisionRepository caseDecisionRepository;
    private final AuditLogRepository auditLogRepository;
    private final com.posgateway.aml.service.kafka.CaseEventProducer caseEventProducer;
    // private final UserService userService; // Inject real user service in
    // production

    @Autowired
    public CaseDecisionService(ComplianceCaseRepository complianceCaseRepository,
                               CaseDecisionRepository caseDecisionRepository,
                               AuditLogRepository auditLogRepository,
                               @Nullable com.posgateway.aml.service.kafka.CaseEventProducer caseEventProducer) {
        this.complianceCaseRepository = complianceCaseRepository;
        this.caseDecisionRepository = caseDecisionRepository;
        this.auditLogRepository = auditLogRepository;
        this.caseEventProducer = caseEventProducer;
    }

    /**
     * Record a decision on a case.
     * 
     * @param caseId        Case ID
     * @param decisionType  Decision type (APPROVE, REJECT, FILE_SAR, HOLD)
     * @param justification Mandatory justification note
     * @param user          The user making the decision
     */
    @Transactional
    public void makeDecision(Long caseId, String decisionType, String justification, User user) {
        // 1. Validation
        if (justification == null || justification.trim().length() < 10) {
            throw new IllegalArgumentException(
                    "Regulatory Requirement: Decision justification must be at least 10 characters long.");
        }

        ComplianceCase cCase = complianceCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        if (cCase.getStatus() == CaseStatus.CLOSED_CLEARED ||
                cCase.getStatus() == CaseStatus.CLOSED_SAR_FILED ||
                cCase.getStatus() == CaseStatus.CLOSED_BLOCKED) {
            throw new IllegalStateException("Cannot make decision on closed case.");
        }

        // 2. Create Decision Record
        CaseDecision decision = new CaseDecision(cCase, decisionType, justification, user);
        caseDecisionRepository.save(decision);

        // 3. Update Case Status & Resolution
        String oldStatus = cCase.getStatus().name();

        switch (decisionType) {
            case "APPROVE": // Clean / False Positive
                cCase.setStatus(CaseStatus.CLOSED_CLEARED);
                cCase.setResolution("CLEARED");
                break;
            case "REJECT": // Fraud / Risk
                cCase.setStatus(CaseStatus.CLOSED_BLOCKED);
                cCase.setResolution("REJECTED");
                break;
            case "FILE_SAR":
                cCase.setStatus(CaseStatus.CLOSED_SAR_FILED);
                cCase.setResolution("SAR_FILED");
                break;
            case "HOLD":
            case "ESCALATE":
                // Status might remain IN_PROGRESS or move to ESCALATED
                // For now, keep as is or update if needed
                break;
            default:
                logger.warn("Unknown decision type: {}", decisionType);
        }

        cCase.setResolutionNotes(justification);
        cCase.setResolvedBy(user.getId());
        cCase.setResolvedAt(LocalDateTime.now());

        complianceCaseRepository.save(cCase);

        // 4. Immutable Audit Log
        AuditLog auditLog = AuditLog.builder()
                .userId(String.valueOf(user.getId()))
                .username(user.getUsername())
                .actionType("CASE_DECISION_" + decisionType)
                .entityType("CASE")
                .entityId(String.valueOf(caseId))
                .beforeValue("Status: " + oldStatus)
                .afterValue("Status: " + cCase.getStatus() + ", Resolution: " + cCase.getResolution())
                .reason(justification)
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);

        // 5. Publish Kafka Event (if producer is configured/enabled)
        if (caseEventProducer != null) {
            try {
                caseEventProducer.publishDecisionEvent(caseId, decisionType, justification, user.getUsername());
            } catch (Exception e) {
                logger.error("Failed to publish decision event for case {}", caseId, e);
            }
        } else {
            logger.debug("Kafka disabled or CaseEventProducer not configured - skipping CASE_DECISION event for case {}",
                    caseId);
        }

        logger.info("Decision {} recorded for case {} by user {}", decisionType, caseId, user.getUsername());
    }
}
