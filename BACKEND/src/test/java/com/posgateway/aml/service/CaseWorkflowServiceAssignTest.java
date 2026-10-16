package com.posgateway.aml.service;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseWorkflowServiceAssignTest {

    /** Verifies first manual assign sets investigation started at once. */
    @Test
    void firstManualAssignSetsInvestigationStartedAtOnce() {
        ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
        UserRepository users = mock(UserRepository.class);
        PermissionService permissions = mock(PermissionService.class);
        ComplianceCase openCase = new ComplianceCase();
        openCase.setId(33L);
        openCase.setStatus(CaseStatus.NEW);
        User assignee = new User();
        assignee.setId(8L);
        User assigner = new User();
        assigner.setId(1L);
        Role role = new Role();
        role.setName("COMPLIANCE_OFFICER");
        assigner.setRole(role);
        when(cases.findById(33L)).thenReturn(Optional.of(openCase));
        when(users.findById(8L)).thenReturn(Optional.of(assignee));
        when(permissions.hasPermission(eq(role), eq(Permission.ASSIGN_CASES))).thenReturn(true);
        when(cases.save(any(ComplianceCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CaseWorkflowService service = new CaseWorkflowService(cases, users, permissions, mock(AuditLogService.class));
        service.assignCase(33L, 8L, assigner);
        LocalDateTime first = openCase.getInvestigationStartedAt();
        assertNotNull(first);

        service.assignCase(33L, 8L, assigner);
        assertSame(first, openCase.getInvestigationStartedAt());
    }
}
