package com.posgateway.aml.repository.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, Long> {
    Optional<RuleDefinition> findByName(String name);
    List<RuleDefinition> findByEnabledTrueOrderByPriorityDesc();
    // HOK-40: PSP isolation filter
    List<RuleDefinition> findByPspId(Long pspId);
}

