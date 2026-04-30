package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.CaseActivity;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.ActivityType;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.CaseActivityRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Case Activity Service
 * Manages activity feed for compliance cases
 */
@Service
public class CaseActivityService {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(CaseActivityService.class);

    private final CaseActivityRepository activityRepository;
    private final ComplianceCaseRepository caseRepository;
    private final UserRepository userRepository;

    @Autowired
    public CaseActivityService(CaseActivityRepository activityRepository,
                               ComplianceCaseRepository caseRepository,
                               UserRepository userRepository) {
        this.activityRepository = activityRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Log a case activity
     */
    @Transactional
    public CaseActivity logActivity(Long caseId, ActivityType activityType, String description,
                                   Long userId, String details, Long relatedEntityId, String relatedEntityType) {
        ComplianceCase complianceCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        CaseActivity activity = CaseActivity.builder()
                .complianceCase(complianceCase)
                .activityType(activityType)
                .description(description)
                .details(details)
                .performedBy(user)
                .performedAt(LocalDateTime.now())
                .relatedEntityId(relatedEntityId)
                .relatedEntityType(relatedEntityType)
                .build();

        return activityRepository.save(activity);
    }

    /**
     * Get activity feed for a case
     */
    public Page<CaseActivity> getActivityFeed(Long caseId, Pageable pageable) {
        return activityRepository.findByComplianceCaseIdOrderByPerformedAtDesc(caseId, pageable);
    }

    /**
     * Get recent activities for a case
     */
    public List<CaseActivity> getRecentActivities(Long caseId, int limit) {
        return activityRepository.findByComplianceCaseIdOrderByPerformedAtDesc(
                caseId, 
                org.springframework.data.domain.PageRequest.of(0, limit)
        ).getContent();
    }

    /**
     * Log case status change
     */
    @Transactional
    public void logStatusChange(Long caseId, CaseStatus oldStatus, CaseStatus newStatus, Long userId) {
        logActivity(caseId, ActivityType.CASE_STATUS_CHANGED,
                String.format("Case status changed from %s to %s", oldStatus, newStatus),
                userId, null, null, null);
    }

    /**
     * Log case assignment
     */
    @Transactional
    public void logAssignment(Long caseId, String assigneeName, Long userId) {
        logActivity(caseId, ActivityType.CASE_ASSIGNED,
                String.format("Case assigned to %s", assigneeName),
                userId, null, null, null);
    }

    /**
     * Log note added
     */
    @Transactional
    public void logNoteAdded(Long caseId, Long noteId, Long userId) {
        logActivity(caseId, ActivityType.NOTE_ADDED,
                "Note added to case",
                userId, null, noteId, "NOTE");
    }

    /**
     * Log evidence attached
     */
    @Transactional
    public void logEvidenceAttached(Long caseId, Long evidenceId, String fileName, Long userId) {
        logActivity(caseId, ActivityType.EVIDENCE_ATTACHED,
                String.format("Evidence attached: %s", fileName),
                userId, null, evidenceId, "EVIDENCE");
    }

    /**
     * Log case escalation
     */
    @Transactional
    public void logEscalation(Long caseId, String escalatedToName, String reason, Long userId) {
        logActivity(caseId, ActivityType.CASE_ESCALATED,
                String.format("Case escalated to %s: %s", escalatedToName, reason),
                userId, reason, null, null);
    }

    /**
     * Log case closure
     */
    @Transactional
    public void logCaseClosed(Long caseId, String resolution, Long userId) {
        logActivity(caseId, ActivityType.CASE_CLOSED,
                String.format("Case closed with resolution: %s", resolution),
                userId, resolution, null, null);
    }
}

