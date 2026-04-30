package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.compliance.CaseQueue;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.CaseQueueRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Case Queue Service
 * Manages case queues for automatic assignment
 */
@Service
public class CaseQueueService {

    private static final Logger logger = LoggerFactory.getLogger(CaseQueueService.class);

    private final CaseQueueRepository queueRepository;
    private final ComplianceCaseRepository caseRepository;
    private final CaseAssignmentService assignmentService;

    @Autowired
    public CaseQueueService(CaseQueueRepository queueRepository,
                            ComplianceCaseRepository caseRepository,
                            CaseAssignmentService assignmentService) {
        this.queueRepository = queueRepository;
        this.caseRepository = caseRepository;
        this.assignmentService = assignmentService;
    }

    /**
     * Add case to a queue
     */
    @Transactional
    public void addCaseToQueue(ComplianceCase complianceCase, String queueName) {
        CaseQueue queue = queueRepository.findByQueueName(queueName)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found: " + queueName));

        if (!queue.getEnabled()) {
            throw new IllegalStateException("Queue is disabled: " + queueName);
        }

        validateCaseEligibility(complianceCase, queue);

        complianceCase.setQueue(queue);
        complianceCase.setStatus(CaseStatus.NEW);
        caseRepository.save(complianceCase);

        logger.info("Added case {} to queue {}", complianceCase.getCaseReference(), queueName);

        if (queue.getAutoAssign()) {
            autoAssignFromQueue(queue);
        }
    }

    /**
     * Validate case eligibility for queue
     */
    private void validateCaseEligibility(ComplianceCase complianceCase, CaseQueue queue) {
        // Check priority
        if (queue.getMinPriority() != null) {
            if (getPriorityOrdinal(complianceCase.getPriority()) < getPriorityOrdinal(queue.getMinPriority())) {
                throw new IllegalArgumentException("Case priority too low for queue");
            }
        }

        // Check queue size limit
        if (queue.getMaxQueueSize() != null) {
            long queueSize = caseRepository.countByQueueAndStatus(queue, CaseStatus.NEW);
            if (queueSize >= queue.getMaxQueueSize()) {
                throw new IllegalStateException("Queue is full: " + queue.getQueueName());
            }
        }
    }

    /**
     * Auto-assign cases from a queue
     */
    @Transactional
    public void autoAssignFromQueue(CaseQueue queue) {
        List<ComplianceCase> queuedCases = caseRepository.findByQueueAndStatus(queue, CaseStatus.NEW);

        int assigned = 0;
        for (ComplianceCase complianceCase : queuedCases) {
            try {
                UserRole targetRole = UserRole.valueOf(queue.getTargetRole());
                assignmentService.assignCaseByWorkload(complianceCase, targetRole);
                complianceCase.setStatus(CaseStatus.ASSIGNED);
                caseRepository.save(complianceCase);
                assigned++;
            } catch (Exception e) {
                logger.warn("Failed to assign case {} from queue {}: {}", 
                        complianceCase.getCaseReference(), 
                        queue.getQueueName(), 
                        e.getMessage());
            }
        }

        if (assigned > 0) {
            logger.info("Auto-assigned {} cases from queue {}", assigned, queue.getQueueName());
        }
    }

    /**
     * Process all auto-assign queues
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    @Transactional
    public void processAutoAssignQueues() {
        List<CaseQueue> autoAssignQueues = queueRepository.findByAutoAssignTrueAndEnabledTrue();
        
        for (CaseQueue queue : autoAssignQueues) {
            autoAssignFromQueue(queue);
        }
    }

    /**
     * Create a new queue
     */
    @Transactional
    public CaseQueue createQueue(String queueName, String targetRole, CasePriority minPriority,
                                 Integer maxQueueSize, Boolean autoAssign) {
        CaseQueue queue = new CaseQueue();
        queue.setQueueName(queueName);
        queue.setTargetRole(targetRole);
        queue.setMinPriority(minPriority);
        queue.setMaxQueueSize(maxQueueSize);
        queue.setAutoAssign(autoAssign != null ? autoAssign : false);
        queue.setEnabled(true);

        return queueRepository.save(queue);
    }

    /**
     * Get priority ordinal for comparison
     */
    private int getPriorityOrdinal(CasePriority priority) {
        return switch (priority) {
            case CRITICAL -> 4;
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    /**
     * Get all queues (for admin / reporting APIs)
     */
    @Transactional(readOnly = true)
    public List<CaseQueue> getAllQueues() {
        return queueRepository.findAll();
    }

    /**
     * Enable/disable a queue
     */
    @Transactional
    public CaseQueue setQueueEnabled(Long queueId, Boolean enabled) {
        CaseQueue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found: " + queueId));
        queue.setEnabled(enabled != null ? enabled : queue.getEnabled());
        return queueRepository.save(queue);
    }

    /**
     * Update queue configuration (partial updates supported)
     */
    @Transactional
    public CaseQueue updateQueue(Long queueId,
                                 Boolean enabled,
                                 Boolean autoAssign,
                                 Integer maxQueueSize,
                                 String targetRole,
                                 CasePriority minPriority) {
        CaseQueue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found: " + queueId));

        if (enabled != null) {
            queue.setEnabled(enabled);
        }
        if (autoAssign != null) {
            queue.setAutoAssign(autoAssign);
        }
        if (maxQueueSize != null) {
            queue.setMaxQueueSize(maxQueueSize);
        }
        if (targetRole != null && !targetRole.isBlank()) {
            queue.setTargetRole(targetRole);
        }
        if (minPriority != null) {
            queue.setMinPriority(minPriority);
        }

        return queueRepository.save(queue);
    }

    /**
     * Trigger processing (auto-assignment) for a queue now.
     */
    @Transactional
    public void processQueue(Long queueId) {
        CaseQueue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found: " + queueId));
        autoAssignFromQueue(queue);
    }
}

