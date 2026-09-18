package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Provisions a PSP's rule profile with editable copies of the system-default rule catalog.
 *
 * <p>The default rules ({@code system_managed = true}, {@code psp_id = null}) are the initial
 * definition for every PSP. When a PSP is onboarded, each default is copied into a PSP-scoped rule
 * that the PSP (and its bank) can edit or disable but <b>cannot delete</b> — the copy carries
 * {@code derivedFromRuleId} pointing back at the default, which the delete guard checks. The PSP may
 * additionally create and delete its own custom rules.
 *
 * <p>Copying is idempotent: a default that already has a copy for the PSP is skipped, so this can be
 * safely re-run to backfill new defaults without duplicating existing copies.
 */
@Service
public class RuleProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(RuleProvisioningService.class);

    private final RuleDefinitionRepository ruleRepository;

    public RuleProvisioningService(RuleDefinitionRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /**
     * Ensure the PSP has a copy of every system-default rule. Returns the number of copies created.
     */
    @Transactional
    public int copyDefaultRulesToPsp(Long pspId) {
        if (pspId == null) {
            return 0;
        }
        List<RuleDefinition> defaults = ruleRepository.findBySystemManagedTrue();
        int created = 0;
        for (RuleDefinition template : defaults) {
            if (ruleRepository.existsByPspIdAndDerivedFromRuleId(pspId, template.getId())) {
                continue;
            }
            ruleRepository.save(cloneForPsp(template, pspId));
            created++;
        }
        if (created > 0) {
            log.info("Provisioned {} default rule copies for PSP {}", created, pspId);
        }
        return created;
    }

    private RuleDefinition cloneForPsp(RuleDefinition template, Long pspId) {
        RuleDefinition copy = new RuleDefinition();
        copy.setName(template.getName());
        copy.setDescription(template.getDescription());
        copy.setRuleJson(template.getRuleJson());
        copy.setDrlContent(template.getDrlContent());
        copy.setRuleType(template.getRuleType());
        copy.setRuleExpression(template.getRuleExpression());
        copy.setScore(template.getScore());
        copy.setAction(template.getAction());
        copy.setPriority(template.getPriority());
        copy.setEnabled(template.isEnabled());
        copy.setPspId(pspId);
        // Editable by the PSP (not system-managed) but not deletable (derived from a default).
        copy.setSystemManaged(false);
        copy.setDerivedFromRuleId(template.getId());
        copy.setCategory(template.getCategory());
        copy.setRuleSubtype(template.getRuleSubtype());
        copy.setAppliesTo(template.getAppliesTo());
        copy.setTypology(template.getTypology());
        copy.setChecksFor(template.getChecksFor());
        // external_code is globally unique (catalog reference for the default); copies leave it null.
        copy.setExternalCode(null);
        copy.setRecommended(template.isRecommended());
        copy.setSampleUseCase(template.getSampleUseCase());
        copy.setParameters(template.getParameters());
        copy.setCreatedBy(template.getCreatedBy());
        return copy;
    }
}
