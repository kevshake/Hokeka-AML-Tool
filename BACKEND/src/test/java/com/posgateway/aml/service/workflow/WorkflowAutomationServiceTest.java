package com.posgateway.aml.service.workflow;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.case_management.CaseAssignmentService;
import com.posgateway.aml.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.posgateway.aml.entity.Role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowAutomationServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private ComplianceCaseRepository complianceCaseRepository;
    @Mock private CaseAssignmentService caseAssignmentService;
    @Mock private NotificationService notificationService;
    @Mock private UserRepository userRepository;

    private WorkflowAutomationService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowAutomationService(merchantRepository, complianceCaseRepository,
                caseAssignmentService, notificationService, userRepository);
    }

    @Test
    void unassignedNewCaseGetsExactlyOneWorkloadAssignment() {
        ComplianceCase openCase = newCase(CaseStatus.NEW, CasePriority.MEDIUM, null);
        User assignee = user(9L, "analyst-1");
        when(caseAssignmentService.assignCaseByWorkload(eq(openCase), eq(UserRole.ANALYST)))
                .thenReturn(assignee);

        service.autoAssignCase(openCase);

        verify(caseAssignmentService, times(1)).assignCaseByWorkload(eq(openCase), eq(UserRole.ANALYST));
        verify(caseAssignmentService, never()).assignCaseByWorkload(eq(openCase), eq(UserRole.COMPLIANCE_OFFICER));
        verify(complianceCaseRepository, never()).save(any());
        verify(userRepository, never()).findByRole_NameAndEnabled(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void alreadyAssignedCaseIsLeftUnchanged() {
        User existing = user(3L, "already-assigned");
        ComplianceCase assigned = newCase(CaseStatus.ASSIGNED, CasePriority.HIGH, existing);

        service.autoAssignCase(assigned);

        verify(caseAssignmentService, never()).assignCaseByWorkload(any(), any());
        verify(complianceCaseRepository, never()).save(any());
        assertEquals(existing, assigned.getAssignedTo());
        assertEquals(CaseStatus.ASSIGNED, assigned.getStatus());
    }

    @Test
    void approveLowActivatesClosesOnboardingAndEmailsOnce() {
        Merchant merchant = merchant(44L, "PENDING");
        ComplianceCase onboarding = onboardingCase(CaseStatus.IN_PROGRESS);
        ComplianceCase investigation = investigationCase(CaseStatus.IN_PROGRESS);
        when(complianceCaseRepository.findByMerchantId(44L)).thenReturn(List.of(onboarding, investigation));

        service.autoApproveLowRisk(merchant, RiskLevel.LOW);

        assertEquals("ACTIVE", merchant.getStatus());
        verify(merchantRepository, times(1)).save(merchant);
        ArgumentCaptor<ComplianceCase> saved = ArgumentCaptor.forClass(ComplianceCase.class);
        verify(complianceCaseRepository, times(1)).save(saved.capture());
        assertEquals(onboarding, saved.getValue());
        assertEquals(CaseStatus.CLOSED_CLEARED, onboarding.getStatus());
        assertEquals(CaseStatus.IN_PROGRESS, investigation.getStatus());
        verify(notificationService, times(1)).sendEmail(eq("ops@example.com"), any(), any());
    }

    @Test
    void approveNonLowDoesNotActivateCloseOrEmail() {
        Merchant merchant = merchant(45L, "PENDING");
        ComplianceCase onboarding = onboardingCase(CaseStatus.NEW);

        service.autoApproveLowRisk(merchant, RiskLevel.MEDIUM);

        assertEquals("PENDING", merchant.getStatus());
        verify(merchantRepository, never()).save(any());
        verify(complianceCaseRepository, never()).findByMerchantId(any());
        verify(complianceCaseRepository, never()).save(any());
        verify(notificationService, never()).sendEmail(any(), any(), any());
        assertNotEquals(CaseStatus.CLOSED_CLEARED, onboarding.getStatus());
        assertNull(onboarding.getResolution());
    }

    @Test
    void slaBreachAssignsAlongHierarchyAndStampsMlroOnce() {
        ComplianceCase overdue = newCase(CaseStatus.IN_PROGRESS, CasePriority.MEDIUM, null);
        overdue.setSlaDeadline(LocalDateTime.now().minusHours(1));
        User mlro = user(12L, "mlro-1");
        Role mlroRole = new Role();
        mlroRole.setName("MLRO");
        mlro.setRole(mlroRole);
        when(complianceCaseRepository.findBySlaDeadlineBeforeAndStatusNot(any(), eq(CaseStatus.CLOSED_CLEARED)))
                .thenReturn(List.of(overdue));
        when(caseAssignmentService.assignCaseByWorkload(eq(overdue), eq(UserRole.COMPLIANCE_OFFICER)))
                .thenReturn(null);
        when(caseAssignmentService.assignCaseByWorkload(eq(overdue), eq(UserRole.MLRO)))
                .thenReturn(mlro);
        when(complianceCaseRepository.findById(100L)).thenReturn(Optional.of(overdue));

        service.escalateOverdueCases();

        verify(caseAssignmentService).assignCaseByWorkload(eq(overdue), eq(UserRole.COMPLIANCE_OFFICER));
        verify(caseAssignmentService).assignCaseByWorkload(eq(overdue), eq(UserRole.MLRO));
        assertEquals(CaseStatus.ESCALATED, overdue.getStatus());
        assertEquals(Boolean.TRUE, overdue.getEscalated());
        assertNotNull(overdue.getMlroNotifiedAt());
        verify(complianceCaseRepository).save(overdue);
    }

    @Test
    void slaBreachSkipsClosedSarAndAlreadyEscalated() {
        ComplianceCase filed = newCase(CaseStatus.CLOSED_SAR_FILED, CasePriority.HIGH, null);
        filed.setId(101L);
        filed.setSlaDeadline(LocalDateTime.now().minusDays(1));
        ComplianceCase already = newCase(CaseStatus.IN_PROGRESS, CasePriority.HIGH, null);
        already.setId(102L);
        already.setEscalated(true);
        already.setSlaDeadline(LocalDateTime.now().minusDays(1));
        when(complianceCaseRepository.findBySlaDeadlineBeforeAndStatusNot(any(), eq(CaseStatus.CLOSED_CLEARED)))
                .thenReturn(List.of(filed, already));

        service.escalateOverdueCases();

        verify(caseAssignmentService, never()).assignCaseByWorkload(any(), any());
        verify(complianceCaseRepository, never()).save(any());
        assertNull(filed.getMlroNotifiedAt());
        assertNull(already.getMlroNotifiedAt());
    }

    private static ComplianceCase newCase(CaseStatus status, CasePriority priority, User assignedTo) {
        ComplianceCase c = new ComplianceCase();
        c.setId(100L);
        c.setCaseReference("CASE-100");
        c.setStatus(status);
        c.setPriority(priority);
        c.setAssignedTo(assignedTo);
        c.setPspId(7L);
        return c;
    }

    private static ComplianceCase onboardingCase(CaseStatus status) {
        ComplianceCase c = new ComplianceCase();
        c.setId(1L);
        c.setCaseReference("ONBOARD-44");
        c.setStatus(status);
        c.setMerchantId(44L);
        return c;
    }

    private static ComplianceCase investigationCase(CaseStatus status) {
        ComplianceCase c = new ComplianceCase();
        c.setId(2L);
        c.setCaseReference("CASE-INV-44");
        c.setStatus(status);
        c.setMerchantId(44L);
        return c;
    }

    private static Merchant merchant(Long id, String status) {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(id);
        merchant.setStatus(status);
        merchant.setContactEmail("ops@example.com");
        return merchant;
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
