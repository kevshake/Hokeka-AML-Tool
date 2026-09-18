package com.posgateway.aml.repository.rules;

import com.posgateway.aml.entity.rules.RuleLifecycleStatus;
import com.posgateway.aml.entity.rules.RuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RuleVersionRepository extends JpaRepository<RuleVersion, Long> {
    List<RuleVersion> findByRuleIdOrderByVersionNumberDesc(Long ruleId);
    Optional<RuleVersion> findFirstByRuleIdOrderByVersionNumberDesc(Long ruleId);
    List<RuleVersion> findByLifecycleStatusOrderBySubmittedAtAsc(RuleLifecycleStatus status);
    List<RuleVersion> findByLifecycleStatusAndEffectiveFromLessThanEqual(
            RuleLifecycleStatus status, LocalDateTime effectiveFrom);
}
