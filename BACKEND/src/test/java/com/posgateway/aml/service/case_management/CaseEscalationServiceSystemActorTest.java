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
import static org.mockito.Mockito.when;

class CaseEscalationServiceSystemActorTest {

    /** Verifies automatic escalation uses system actor instead of looking up null user. */
    @Test
    void automaticEscalationUsesSystemActorInsteadOfLookingUpNullUser() {
        ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
        UserRepository users = mock(UserRepository.class);
        EscalationRuleRepository rules = mock(EscalationRuleRepository.class);
        CaseWorkflowService workflow = mock(CaseWorkflowService.class);
        CaseActivityService activity = mock(CaseActivityService.class);

        ComplianceCase openCase = new ComplianceCase();
        openCase.setId(55L);
        openCase.setStatus(CaseStatus.IN_PROGRESS);
        openCase.setPriority(CasePriority.HIGH);
        openCase.setEscalated(false);
        when(cases.findById(55L)).thenReturn(Optional.of(openCase));
        when(rules.findByEnabledTrue()).thenReturn(List.of());
        User officer = new User();
        officer.setId(9L);
        officer.setUsername("co-1");
        Role role = new Role();
        role.setName(UserRole.COMPLIANCE_OFFICER.name());
        officer.setRole(role);
        when(users.findByRole_NameAndEnabled(UserRole.COMPLIANCE_OFFICER.name(), true))
                .thenReturn(List.of(officer));

        CaseEscalationService service = new CaseEscalationService(
                cases,
                mock(TransactionRepository.class),
                mock(CustomerRiskProfilingService.class),
                rules,
                users,
                workflow,
                activity);
        service.escalateCase(55L, "SLA / rule auto-escalation", null);

        verify(users, never()).findById(isNull());
        verify(workflow).escalateCase(eq(55L), eq(9L), eq("SLA / rule auto-escalation"), isNull());
        verify(activity).logEscalation(55L, "co-1", "SLA / rule auto-escalation", null);
    }
}
