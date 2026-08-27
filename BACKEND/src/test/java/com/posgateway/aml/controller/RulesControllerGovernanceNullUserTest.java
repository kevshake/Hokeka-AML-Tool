package com.posgateway.aml.controller;

import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.service.ai.AiRuleGeneratorService;
import com.posgateway.aml.service.rules.DroolsRulesService;
import com.posgateway.aml.service.rules.DynamicRuleConverter;
import com.posgateway.aml.service.rules.RuleEffectivenessService;
import com.posgateway.aml.service.rules.RuleGovernanceService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the fix for W18-4: RulesController.approveVersion/rejectVersion/proposeRollback used
 * to pass getCurrentUser() straight into RuleGovernanceService unguarded. When the authenticated
 * principal's username doesn't resolve via UserRepository (an edge case, but real -- e.g. a stale
 * session after the user row was deleted), getCurrentUser() returns null and the governance
 * service NPEs internally (version.getCreatedBy().getId().equals(reviewer.getId()) on a null
 * reviewer), surfacing as an opaque 500 instead of a proper 401.
 */
class RulesControllerGovernanceNullUserTest {

    private RulesController controller;
    private RuleGovernanceService governanceService;

    private void setUp() {
        UserRepository userRepository = mock(UserRepository.class);
        // The authenticated principal's username never resolves -- exactly the edge case.
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());

        governanceService = mock(RuleGovernanceService.class);

        controller = new RulesController(
                mock(RuleDefinitionRepository.class),
                mock(AiRuleGeneratorService.class),
                mock(DroolsRulesService.class),
                mock(DynamicRuleConverter.class),
                mock(PspIsolationService.class),
                userRepository,
                mock(RuleEffectivenessService.class),
                governanceService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost-user", "n/a", Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approveVersionReturns401InsteadOfNpeWhenCurrentUserIsUnresolvable() {
        setUp();
        RulesController.ReviewRuleVersionRequest request =
                new RulesController.ReviewRuleVersionRequest("reason", null);

        ResponseEntity<RuleGovernanceService.RuleVersionView> response =
                controller.approveVersion(1L, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(governanceService, never()).approve(any(), any(), any(), any());
    }

    @Test
    void rejectVersionReturns401InsteadOfNpeWhenCurrentUserIsUnresolvable() {
        setUp();
        RulesController.ReviewRuleVersionRequest request =
                new RulesController.ReviewRuleVersionRequest("reason", null);

        ResponseEntity<RuleGovernanceService.RuleVersionView> response =
                controller.rejectVersion(1L, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(governanceService, never()).reject(any(), any(), any());
    }

    @Test
    void proposeRollbackReturns401InsteadOfNpeWhenCurrentUserIsUnresolvable() {
        setUp();
        RulesController.RollbackRuleRequest request =
                new RulesController.RollbackRuleRequest(2L, "reason");

        ResponseEntity<?> response = controller.proposeRollback(1L, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(governanceService, never()).proposeRollback(any(), any(), any(), any());
    }
}
