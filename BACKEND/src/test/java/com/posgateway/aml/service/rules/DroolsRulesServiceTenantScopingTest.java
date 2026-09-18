package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.rules.RuleEvaluationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * A real Drools compile+fire test proving tenant isolation: a PSP-owned DB DRL rule fires only on
 * that PSP's transactions (never cross-tenant), while a global rule fires for everyone.
 */
@ExtendWith(MockitoExtension.class)
class DroolsRulesServiceTenantScopingTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private RuleDefinitionRepository ruleRepository;

    private DroolsRulesService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new DroolsRulesService(redisTemplate, ruleRepository);
    }

    private RuleDefinition drlRule(String name, Long pspId, String marker) {
        RuleDefinition r = new RuleDefinition();
        r.setName(name);
        r.setEnabled(true);
        r.setPspId(pspId);
        r.setDrlContent(
                "package rules;\n"
                        + "import com.posgateway.aml.rules.TransactionFact;\n"
                        + "rule \"" + name + "\"\n"
                        + "when\n"
                        + "  $f : TransactionFact()\n"
                        + "then\n"
                        + "  $f.getTriggeredRules().add(\"" + marker + "\");\n"
                        + "end\n");
        return r;
    }

    @Test
    void pspOwnedDrlRuleFiresOnlyForItsOwnTenant() {
        when(ruleRepository.findByEnabledTrueOrderByPriorityDesc()).thenReturn(List.of(
                drlRule("psp1-rule", 1L, "psp1-fired"),
                drlRule("psp2-rule", 2L, "psp2-fired")));
        service.reloadRules();

        RuleEvaluationResult forPsp1 = service.evaluate(100L, Map.of("pspId", 1), 0.5);
        assertTrue(forPsp1.getTriggeredRules().contains("psp1-fired"), "PSP 1's rule must fire for PSP 1");
        assertFalse(forPsp1.getTriggeredRules().contains("psp2-fired"), "PSP 2's rule must NOT fire for PSP 1");

        RuleEvaluationResult forPsp2 = service.evaluate(101L, Map.of("pspId", 2), 0.5);
        assertTrue(forPsp2.getTriggeredRules().contains("psp2-fired"), "PSP 2's rule must fire for PSP 2");
        assertFalse(forPsp2.getTriggeredRules().contains("psp1-fired"), "PSP 1's rule must NOT fire for PSP 2");
    }

    @Test
    void globalRuleFiresForEveryTenant() {
        when(ruleRepository.findByEnabledTrueOrderByPriorityDesc())
                .thenReturn(List.of(drlRule("global-rule", null, "global-fired")));
        service.reloadRules();

        assertTrue(service.evaluate(1L, Map.of("pspId", 7), 0.5).getTriggeredRules().contains("global-fired"));
        assertTrue(service.evaluate(2L, Map.of("pspId", 9), 0.5).getTriggeredRules().contains("global-fired"));
    }

    @Test
    void oneMalformedDrlIsSkippedAndTheEngineStaysUp() {
        RuleDefinition good = drlRule("good-rule", null, "good-fired");
        RuleDefinition broken = new RuleDefinition();
        broken.setName("broken-rule");
        broken.setEnabled(true);
        broken.setDrlContent("this is not valid drl at all {{{");
        when(ruleRepository.findByEnabledTrueOrderByPriorityDesc()).thenReturn(List.of(good, broken));

        service.reloadRules();

        assertTrue(service.isDroolsEnabled(), "engine must stay active despite one bad rule");
        assertEquals(1, service.getSkippedDrlRuleCount(), "the one malformed rule is skipped");
        assertTrue(service.evaluate(1L, Map.of("pspId", 3), 0.5).getTriggeredRules().contains("good-fired"),
                "the valid rule still fires");
    }
}
