package com.posgateway.aml.repository.limits;

import com.posgateway.aml.entity.limits.VelocityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VelocityRuleRepository extends JpaRepository<VelocityRule, Long> {
    Optional<VelocityRule> findByRuleName(String ruleName);
    List<VelocityRule> findByStatus(String status);
    List<VelocityRule> findByRiskLevel(String riskLevel);
}

