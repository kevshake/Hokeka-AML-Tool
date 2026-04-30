package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.EscalationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Escalation Rules
 */
@Repository
public interface EscalationRuleRepository extends JpaRepository<EscalationRule, Long> {

    /**
     * Find all enabled escalation rules
     */
    List<EscalationRule> findByEnabledTrue();

    /**
     * Find escalation rule by name
     */
    EscalationRule findByRuleName(String ruleName);
}

