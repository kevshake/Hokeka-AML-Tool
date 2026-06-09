package com.posgateway.aml.repository.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, Long> {
    Optional<RuleDefinition> findByName(String name);
    List<RuleDefinition> findByEnabledTrueOrderByPriorityDesc();
    List<RuleDefinition> findByPspId(Long pspId);

    /** Global system rules (psp_id IS NULL) plus PSP-specific overrides. */
    @Query("SELECT r FROM RuleDefinition r WHERE r.pspId IS NULL OR r.pspId = :pspId ORDER BY r.priority ASC, r.name ASC")
    List<RuleDefinition> findVisibleForPsp(@Param("pspId") Long pspId);
}
