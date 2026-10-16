package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.EscalationRuleRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.CaseWorkflowService;
import com.posgateway.aml.service.risk.CustomerRiskProfilingService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.exception.ConflictException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Regression (sim run 2026-10-16): POST /cases/{id}/escalate picked escalation targets from every
 * tenant's MLRO / compliance officers and answered 500 when none existed. Targets are now limited
 * to the case's PSP, and "nobody to escalate to" is a 409.
 */
class CaseEscalationTenancyTest {

    private final ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EscalationRuleRepository rules = mock(EscalationRuleRepository.class);
    private final CaseWorkflowService workflow = mock(CaseWorkflowService.class);
    private final CaseActivityService activity = mock(CaseActivityService.class);

    private CaseEscalationService service() {
        return new CaseEscalationService(cases, mock(TransactionRepository.class),
                mock(CustomerRiskProfilingService.class), rules, users, workflow, activity);
    }

    private static User user(long id, String roleName, Long pspId) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        Role role = new Role();
        role.setName(roleName);
        u.setRole(role);
        if (pspId != null) {
            Psp psp = new Psp();
            psp.setPspId(pspId);
            u.setPsp(psp);
        }
        return u;
    }

    private ComplianceCase caseOf(long pspId, User assignee) {
        ComplianceCase c = new ComplianceCase();
        c.setId(77L);
        c.setPspId(pspId);
        c.setStatus(CaseStatus.IN_PROGRESS);
        c.setPriority(CasePriority.HIGH);
        c.setAssignedTo(assignee);
        when(cases.findById(77L)).thenReturn(Optional.of(c));
        when(rules.findByEnabledTrue()).thenReturn(List.of());
        return c;
    }

    @Test
    void anotherPspsMlroIsNeverChosen() {
        User assignee = user(1L, "COMPLIANCE_OFFICER", 5L);
        caseOf(5L, assignee);
        when(users.findByRole_NameAndEnabled("MLRO", true))
                .thenReturn(List.of(user(2L, "MLRO", 6L), user(3L, "MLRO", 5L)));
        when(users.findById(1L)).thenReturn(Optional.of(assignee));

        service().escalateCase(77L, "needs MLRO", 1L);

        verify(workflow).escalateCase(eq(77L), eq(3L), eq("needs MLRO"), eq(assignee));
    }

    @Test
    void noInTenantTargetIsAConflict() {
        User assignee = user(1L, "COMPLIANCE_OFFICER", 5L);
        caseOf(5L, assignee);
        when(users.findByRole_NameAndEnabled("MLRO", true)).thenReturn(List.of(user(2L, "MLRO", 6L)));

        assertThatThrownBy(() -> service().escalateCase(77L, "needs MLRO", 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MLRO");
    }
}
