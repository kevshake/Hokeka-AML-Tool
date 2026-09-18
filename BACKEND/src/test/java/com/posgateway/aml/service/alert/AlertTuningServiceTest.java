package com.posgateway.aml.service.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.alert.AlertTuningRecommendation;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.alert.AlertTuningRecommendationRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.service.rules.RuleEffectivenessService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertTuningServiceTest {

    @Mock private AlertTuningRecommendationRepository recommendationRepository;
    @Mock private RuleDefinitionRepository ruleRepository;
    @Mock private RuleEffectivenessService effectivenessService;
    @Mock private UserRepository userRepository;
    @Mock private PspIsolationService pspIsolationService;
    @Mock private com.posgateway.aml.service.rules.RuleGovernanceService governanceService;

    private AlertTuningService service;

    @BeforeEach
    void setUp() {
        service = new AlertTuningService(recommendationRepository, ruleRepository, effectivenessService,
                userRepository, pspIsolationService, new ObjectMapper(), governanceService);
    }

    @Test
    void proposesConcreteThresholdChangeFromObservedFalsePositives() {
        RuleDefinition rule = rule(10L, "Velocity Rule", "{\"count_threshold\":100,\"lookback_days\":30}");
        when(ruleRepository.findFirstByNameOrderByIdAsc(rule.getName())).thenReturn(Optional.of(rule));
        when(recommendationRepository.findByRuleIdAndStatus(10L, "PENDING")).thenReturn(List.of());
        when(effectivenessService.compute(10L)).thenReturn(new RuleEffectivenessService.RuleEffectivenessDTO(
                10L, 1_000, 40, 60, 0.60, 1.5, Instant.now()));
        when(recommendationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AlertTuningRecommendation recommendation = service.suggestTuning(rule.getName());

        assertEquals("PENDING", recommendation.getStatus());
        assertTrue(recommendation.getProposedParameters().contains("125"));
        assertEquals(0.60, recommendation.getFalsePositiveRate());
    }

    @Test
    void applyingRecommendationUpdatesLiveRuleParameters() {
        RuleDefinition rule = rule(10L, "Velocity Rule", "{\"count_threshold\":100}");
        AlertTuningRecommendation recommendation = new AlertTuningRecommendation();
        recommendation.setRuleId(10L);
        recommendation.setStatus("PENDING");
        recommendation.setOriginalParameters("{\"count_threshold\":100}");
        recommendation.setProposedParameters("{\"count_threshold\":125}");
        User user = new User();
        user.setId(5L);

        when(recommendationRepository.findById(20L)).thenReturn(Optional.of(recommendation));
        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(pspIsolationService.isPlatformAdministrator(user)).thenReturn(true);

        service.applyRecommendation(20L, "admin");

        assertEquals("APPLIED", recommendation.getStatus());
        assertEquals(5L, recommendation.getAppliedBy());

        // The tuned parameters must go through maker/checker, NOT a direct repository write: a
        // direct save produced no RuleVersion, needed no second approver and skipped the engine
        // reload, leaving holes in the rule_versions audit trail.
        org.mockito.ArgumentCaptor<RuleDefinition> patch =
                org.mockito.ArgumentCaptor.forClass(RuleDefinition.class);
        verify(governanceService).proposeUpdate(org.mockito.ArgumentMatchers.eq(rule), patch.capture(),
                org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.anyString());
        assertEquals("{\"count_threshold\":125}", patch.getValue().getParameters());
        // Only the parameters are proposed — no other field is silently altered.
        assertNull(patch.getValue().getRuleExpression());
        assertNull(patch.getValue().getAction());
        verify(ruleRepository, org.mockito.Mockito.never()).save(any(RuleDefinition.class));
    }

    private static RuleDefinition rule(Long id, String name, String parameters) {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(id);
        rule.setName(name);
        rule.setParameters(parameters);
        return rule;
    }
}
