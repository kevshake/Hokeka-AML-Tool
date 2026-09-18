package com.posgateway.aml.repository.policy;

import com.posgateway.aml.entity.policy.AmlPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AmlPolicyRepository extends JpaRepository<AmlPolicy, Long> {
    Optional<AmlPolicy> findByPolicyNameAndIsActive(String policyName, boolean isActive);
    List<AmlPolicy> findByPolicyName(String policyName);
    List<AmlPolicy> findByIsActive(boolean isActive);
}

