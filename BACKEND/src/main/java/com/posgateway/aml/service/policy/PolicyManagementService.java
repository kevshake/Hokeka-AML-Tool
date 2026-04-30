package com.posgateway.aml.service.policy;

import com.posgateway.aml.entity.policy.AmlPolicy;
import com.posgateway.aml.entity.policy.PolicyAcknowledgment;
import com.posgateway.aml.repository.policy.AmlPolicyRepository;
import com.posgateway.aml.repository.policy.PolicyAcknowledgmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Policy Management Service
 * Manages AML policy documents with version control and acknowledgment tracking
 */
@Service
public class PolicyManagementService {

    private static final Logger logger = LoggerFactory.getLogger(PolicyManagementService.class);

    private final AmlPolicyRepository policyRepository;
    private final PolicyAcknowledgmentRepository acknowledgmentRepository;

    @Autowired
    public PolicyManagementService(
            AmlPolicyRepository policyRepository,
            PolicyAcknowledgmentRepository acknowledgmentRepository) {
        this.policyRepository = policyRepository;
        this.acknowledgmentRepository = acknowledgmentRepository;
    }

    /**
     * Create new policy version
     */
    @Transactional
    public AmlPolicy createPolicyVersion(String policyName, String version, String description,
                                         String content, String documentPath, Long createdBy) {
        // Deactivate previous version
        Optional<AmlPolicy> previousActive = policyRepository.findByPolicyNameAndIsActive(policyName, true);
        if (previousActive.isPresent()) {
            AmlPolicy previous = previousActive.get();
            previous.setIsActive(false);
            policyRepository.save(previous);
        }

        // Create new version
        AmlPolicy policy = new AmlPolicy();
        policy.setPolicyName(policyName);
        policy.setVersion(version);
        policy.setDescription(description);
        policy.setContent(content);
        policy.setDocumentPath(documentPath);
        policy.setCreatedBy(createdBy);
        policy.setIsActive(false); // Not active until approved
        policy.setCreatedAt(LocalDateTime.now());
        policy.setUpdatedAt(LocalDateTime.now());

        logger.info("Created new policy version: {} v{}", policyName, version);
        return policyRepository.save(policy);
    }

    /**
     * Approve and activate policy version
     */
    @Transactional
    public AmlPolicy approvePolicy(Long policyId, Long approvedBy) {
        AmlPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyId));

        // Deactivate other versions of same policy
        List<AmlPolicy> otherVersions = policyRepository.findByPolicyName(policy.getPolicyName());
        for (AmlPolicy other : otherVersions) {
            if (!other.getId().equals(policyId)) {
                other.setIsActive(false);
                policyRepository.save(other);
            }
        }

        policy.setIsActive(true);
        policy.setApprovedBy(approvedBy);
        policy.setApprovedAt(LocalDateTime.now());
        policy.setEffectiveDate(LocalDateTime.now());
        policy.setUpdatedAt(LocalDateTime.now());

        logger.info("Approved and activated policy: {} v{}", policy.getPolicyName(), policy.getVersion());
        return policyRepository.save(policy);
    }

    /**
     * Acknowledge policy
     */
    @Transactional
    public PolicyAcknowledgment acknowledgePolicy(Long policyId, Long userId, String ipAddress) {
        AmlPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyId));

        // Check if already acknowledged
        Optional<PolicyAcknowledgment> existing = acknowledgmentRepository.findByPolicyIdAndUserId(policyId, userId);
        if (existing.isPresent()) {
            logger.debug("User {} already acknowledged policy {}", userId, policyId);
            return existing.get();
        }

        PolicyAcknowledgment acknowledgment = new PolicyAcknowledgment();
        acknowledgment.setPolicy(policy);
        acknowledgment.setUserId(userId);
        acknowledgment.setIpAddress(ipAddress);
        acknowledgment.setAcknowledgedAt(LocalDateTime.now());

        logger.info("User {} acknowledged policy {} v{}", userId, policy.getPolicyName(), policy.getVersion());
        return acknowledgmentRepository.save(acknowledgment);
    }

    /**
     * Get active policy
     */
    public Optional<AmlPolicy> getActivePolicy(String policyName) {
        return policyRepository.findByPolicyNameAndIsActive(policyName, true);
    }

    /**
     * Get all versions of a policy
     */
    public List<AmlPolicy> getPolicyVersions(String policyName) {
        return policyRepository.findByPolicyName(policyName);
    }

    /**
     * Check if user has acknowledged policy
     */
    public boolean hasUserAcknowledged(Long policyId, Long userId) {
        return acknowledgmentRepository.findByPolicyIdAndUserId(policyId, userId).isPresent();
    }

    /**
     * Get policies requiring review
     */
    public List<AmlPolicy> getPoliciesRequiringReview() {
        LocalDateTime now = LocalDateTime.now();
        return policyRepository.findAll().stream()
                .filter(p -> p.getReviewDate() != null && p.getReviewDate().isBefore(now))
                .toList();
    }
}

