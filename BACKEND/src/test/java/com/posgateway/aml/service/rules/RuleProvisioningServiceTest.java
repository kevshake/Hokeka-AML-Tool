package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers provisioning a PSP's profile with editable-but-undeletable copies of the default rules.
 */
@ExtendWith(MockitoExtension.class)
class RuleProvisioningServiceTest {

    @Mock
    private RuleDefinitionRepository ruleRepository;
    @InjectMocks
    private RuleProvisioningService service;

    private RuleDefinition systemDefault() {
        RuleDefinition d = new RuleDefinition();
        d.setId(10L);
        d.setName("R-1 High Velocity");
        d.setSystemManaged(true);
        d.setEnabled(true);
        d.setRuleType("SPEL");
        d.setRuleExpression("#tx.amount > 1000");
        d.setAction("BLOCK");
        d.setExternalCode("R-1");
        d.setCategory("FRAUD");
        return d;
    }

    @Test
    void copiesDefaultAsEditableUndeletablePspRule() {
        RuleDefinition def = systemDefault();
        when(ruleRepository.findBySystemManagedTrue()).thenReturn(List.of(def));
        when(ruleRepository.existsByPspIdAndDerivedFromRuleId(5L, 10L)).thenReturn(false);

        int created = service.copyDefaultRulesToPsp(5L);

        assertEquals(1, created);
        ArgumentCaptor<RuleDefinition> captor = ArgumentCaptor.forClass(RuleDefinition.class);
        verify(ruleRepository).save(captor.capture());
        RuleDefinition copy = captor.getValue();
        assertEquals(5L, copy.getPspId());
        assertFalse(copy.isSystemManaged(), "PSP copy must be editable (not system-managed)");
        assertEquals(10L, copy.getDerivedFromRuleId(), "PSP copy must record its source default (undeletable)");
        assertEquals("R-1 High Velocity", copy.getName());
        assertEquals("#tx.amount > 1000", copy.getRuleExpression());
        assertEquals("BLOCK", copy.getAction());
        assertNull(copy.getExternalCode(), "globally-unique external code must not be copied");
    }

    @Test
    void skipsDefaultAlreadyCopiedForThePsp() {
        RuleDefinition def = systemDefault();
        when(ruleRepository.findBySystemManagedTrue()).thenReturn(List.of(def));
        when(ruleRepository.existsByPspIdAndDerivedFromRuleId(5L, 10L)).thenReturn(true);

        int created = service.copyDefaultRulesToPsp(5L);

        assertEquals(0, created);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void nullPspIsANoOp() {
        assertEquals(0, service.copyDefaultRulesToPsp(null));
        verifyNoInteractions(ruleRepository);
    }
}
