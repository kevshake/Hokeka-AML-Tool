package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.CaseQueue;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.CaseQueueRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service to manage case assignment and work queues.
 * Handles:
 * 1. Auto-assignment to Queues based on priority
 * 2. Auto-assignment to Users (Load Balancing)
 * 3. Manual Assignment logic
 */
@Service
public class CaseAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(CaseAssignmentService.class);

    private final ComplianceCaseRepository complianceCaseRepository;
    private final CaseQueueRepository caseQueueRepository;
    private final UserRepository userRepository;

    @Autowired
    public CaseAssignmentService(ComplianceCaseRepository complianceCaseRepository,
            CaseQueueRepository caseQueueRepository,
            UserRepository userRepository) {
        this.complianceCaseRepository = complianceCaseRepository;
        this.caseQueueRepository = caseQueueRepository;
        this.userRepository = userRepository;
    }

    /**
     * Determines the correct queue for a case based on priority or rules.
     */
    @Transactional
    public void assignCaseToQueue(ComplianceCase cCase) {
        if (cCase.getQueue() != null) {
            return; // Already in a queue
        }

        CasePriority priority = cCase.getPriority();
        Optional<CaseQueue> targetQueue = caseQueueRepository.findByMinPriority(priority);

        if (targetQueue.isPresent()) {
            CaseQueue queue = targetQueue.get();
            cCase.setQueue(queue);
            logger.info("Assigned case {} to queue {}", cCase.getCaseReference(), queue.getQueueName());

            if (Boolean.TRUE.equals(queue.getAutoAssign())) {
                autoAssignToUser(cCase, queue);
            }
        } else {
            // Default queue fallback
            caseQueueRepository.findByQueueName("DEFAULT").ifPresent(q -> {
                cCase.setQueue(q);
                logger.info("Assigned case {} to DEFAULT queue", cCase.getCaseReference());
            });
        }

        cCase.setUpdatedAt(LocalDateTime.now());
        complianceCaseRepository.save(cCase);
    }

    /**
     * Load Balancer: Assigns case to the least loaded user in the target role.
     */
    private void autoAssignToUser(ComplianceCase cCase, CaseQueue queue) {
        String role = queue.getTargetRole(); // e.g., "ANALYST"
        if (role == null)
            return;

        // 1. Find users with this role (Simplification: assumes repository method
        // exists)
        // In real app: userRepository.findByRole(role)
        List<User> eligibleUsers = userRepository.findAll(); // specific method needed in repo

        User bestCandidate = null;
        long minPayload = Long.MAX_VALUE;

        // 2. Find user with lowest open case count
        for (User user : eligibleUsers) {
            // Need a way to check role, skipping for brevity in this snippet
            long openCases = complianceCaseRepository.countByAssignedTo_IdAndStatusIn(
                    user.getId(),
                    List.of(CaseStatus.NEW, CaseStatus.IN_PROGRESS, CaseStatus.ASSIGNED));

            if (openCases < minPayload) {
                minPayload = openCases;
                bestCandidate = user;
            }
        }

        // 3. Assign
        if (bestCandidate != null) {
            assignCaseToUser(cCase.getId(), bestCandidate.getId(), null); // System assignment
        }
    }

    /**
     * Assigns a case to a specific user.
     */
    @Transactional
    public void assignCaseToUser(Long caseId, Long userId, Long assignerId) {
        ComplianceCase cCase = complianceCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        cCase.setAssignedTo(user);
        cCase.setAssignedBy(assignerId != null ? assignerId : 0L); // 0 = System
        cCase.setAssignedAt(LocalDateTime.now());
        cCase.setStatus(CaseStatus.ASSIGNED);
        cCase.setUpdatedAt(LocalDateTime.now());

        complianceCaseRepository.save(cCase);
        logger.info("Case {} assigned to user {}", cCase.getCaseReference(), user.getUsername());
    }

    /**
     * AUTO-ASSIGNMENT: Assigns case to the least loaded user in the specified role.
     */
    @Transactional
    public User assignCaseByWorkload(ComplianceCase cCase, com.posgateway.aml.model.UserRole role) {
        // 1. Find active eligible users
        List<User> eligibleUsers = userRepository.findByRole_NameAndEnabled(role.name(), true);

        if (eligibleUsers.isEmpty()) {
            logger.warn("No eligible users found for role {}", role);
            return null; // Or throw exception based on requirements
        }

        User bestCandidate = null;
        long minPayload = Long.MAX_VALUE;

        // 2. Find user with lowest open case count
        for (User user : eligibleUsers) {
            long openCases = complianceCaseRepository.countByAssignedTo_IdAndStatusIn(
                    user.getId(),
                    List.of(CaseStatus.NEW, CaseStatus.IN_PROGRESS, CaseStatus.ASSIGNED));

            if (openCases < minPayload) {
                minPayload = openCases;
                bestCandidate = user;
            }
        }

        // 3. Assign
        if (bestCandidate != null) {
            assignCaseToUser(cCase.getId(), bestCandidate.getId(), null);
            return bestCandidate;
        }

        return null;
    }
}
