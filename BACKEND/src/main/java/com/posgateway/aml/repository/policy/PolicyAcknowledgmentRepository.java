package com.posgateway.aml.repository.policy;

import com.posgateway.aml.entity.policy.PolicyAcknowledgment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyAcknowledgmentRepository extends JpaRepository<PolicyAcknowledgment, Long> {
    Optional<PolicyAcknowledgment> findByPolicyIdAndUserId(Long policyId, Long userId);
    List<PolicyAcknowledgment> findByPolicyId(Long policyId);
    List<PolicyAcknowledgment> findByUserId(Long userId);
}

