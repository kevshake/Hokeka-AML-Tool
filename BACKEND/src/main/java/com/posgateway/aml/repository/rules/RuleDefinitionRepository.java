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

    /**
     * First rule matching a name. Rule names are no longer globally unique (a system default and
     * each PSP's copy share a name), so callers that only have a name resolve to a representative
     * row; ownership-sensitive callers must use {@link #findByNameAndPspId}.
     */
    Optional<RuleDefinition> findFirstByNameOrderByIdAsc(String name);

    /** Name uniqueness is scoped per owner (system default = null pspId, or a specific PSP). */
    Optional<RuleDefinition> findByNameAndPspId(String name, Long pspId);

    List<RuleDefinition> findByEnabledTrueOrderByPriorityDesc();
    List<RuleDefinition> findByPspId(Long pspId);

    /** The curated system-default catalog (templates copied into each PSP profile). */
    List<RuleDefinition> findBySystemManagedTrue();

    boolean existsByPspId(Long pspId);

    boolean existsByPspIdAndDerivedFromRuleId(Long pspId, Long derivedFromRuleId);

    /** Enabled rules owned by one PSP (that PSP's copies + its own custom rules). */
    List<RuleDefinition> findByEnabledTrueAndPspIdOrderByPriorityDesc(Long pspId);

    /** Enabled global system rules — the fallback for a PSP that has no copies yet. */
    List<RuleDefinition> findByEnabledTrueAndPspIdIsNullOrderByPriorityDesc();

    /** Global system rules (psp_id IS NULL) plus PSP-specific overrides. */
    @Query("SELECT r FROM RuleDefinition r WHERE r.pspId IS NULL OR r.pspId = :pspId ORDER BY r.priority ASC, r.name ASC")
    List<RuleDefinition> findVisibleForPsp(@Param("pspId") Long pspId);
}
