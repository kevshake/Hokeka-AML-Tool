package com.posgateway.aml.repository.limits;

import com.posgateway.aml.entity.limits.VelocityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VelocityRuleRepository extends JpaRepository<VelocityRule, Long> {
    Optional<VelocityRule> findByRuleNameAndPspId(String ruleName, Long pspId);
    Optional<VelocityRule> findByRuleNameAndPspIdIsNull(String ruleName);
    List<VelocityRule> findByPspId(Long pspId);
    List<VelocityRule> findByPspIdIsNull();
    List<VelocityRule> findByStatus(String status);
    List<VelocityRule> findByStatusAndPspId(String status, Long pspId);
    List<VelocityRule> findByStatusAndPspIdIsNull(String status);
    List<VelocityRule> findByRiskLevel(String riskLevel);
}

