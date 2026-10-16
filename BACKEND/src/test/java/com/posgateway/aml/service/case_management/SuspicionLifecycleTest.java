package com.posgateway.aml.service.case_management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.AlertDisposition;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.CaseQueueRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.service.workflow.WorkflowAutomationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SuspicionLifecycleTest {

    @Test
    void monitoringCaseCreationPopulatesSuspicionFormedAt() {
        ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
        when(cases.findByMerchantId(5L)).thenReturn(List.of());
        when(cases.save(any(ComplianceCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RuleDefinitionRepository rules = mock(RuleDefinitionRepository.class);
        when(rules.findFirstByNameOrderByIdAsc(any())).thenReturn(Optional.empty());

        CaseCreationService service = new CaseCreationService(cases, new ObjectMapper(),
                null, null, mock(CaseEnrichmentService.class), rules,
                mock(com.posgateway.aml.service.residency.TransactionReferenceResolver.class));
        TransactionEntity tx = new TransactionEntity();
        tx.setMerchantId("5");
        tx.setPspId(9L);

        service.triggerCaseFromRule(tx, "R-171", "amount z-score");

        org.mockito.ArgumentCaptor<ComplianceCase> captor =
                org.mockito.ArgumentCaptor.forClass(ComplianceCase.class);
        org.mockito.Mockito.verify(cases).save(captor.capture());
        assertNotNull(captor.getValue().getSuspicionFormedAt());
        assertNullSafeOpen(captor.getValue());
    }

    @Test
    void firstAssignmentSetsInvestigationStartedAtOnce() {
        ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
        UserRepository users = mock(UserRepository.class);
        ComplianceCase openCase = new ComplianceCase();
        openCase.setId(21L);
        openCase.setPspId(3L);
        openCase.setStatus(CaseStatus.NEW);
        openCase.setPriority(CasePriority.MEDIUM);
        User analyst = user(8L, 3L);
        when(cases.findById(21L)).thenReturn(Optional.of(openCase));
        when(users.findById(8L)).thenReturn(Optional.of(analyst));
        when(cases.save(any(ComplianceCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CaseAssignmentService service = new CaseAssignmentService(cases, mock(CaseQueueRepository.class), users);
        service.assignCaseToUser(21L, 8L, null);
        LocalDateTime first = openCase.getInvestigationStartedAt();
        assertNotNull(first);

        service.assignCaseToUser(21L, 8L, 99L);
        assertSame(first, openCase.getInvestigationStartedAt());
    }

    @Test
    void alertToCaseCopiesSuspicionTimestampOntoNewCase() {
        AlertRepository alerts = mock(AlertRepository.class);
        ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
        Alert alert = new Alert();
        alert.setAlertId(70L);
        alert.setMerchantId(6L);
        alert.setPspId(4L);
        alert.setScore(0.5);
        alert.setAction("HOLD");
        LocalDateTime formed = LocalDateTime.of(2026, 9, 1, 8, 0);
        alert.setSuspicionFormedAt(formed);
        when(alerts.findById(70L)).thenReturn(Optional.of(alert));
        when(cases.findByMerchantId(6L)).thenReturn(List.of());
        when(cases.save(any(ComplianceCase.class))).thenAnswer(invocation -> {
            ComplianceCase saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(400L);
            }
            return saved;
        });

        AlertToCaseService service = new AlertToCaseService(alerts, cases, mock(MerchantRepository.class),
                mock(WorkflowAutomationService.class), mock(CaseActivityService.class),
                mock(CaseEscalationService.class),
                mock(com.posgateway.aml.service.compliance.ComplianceEntityLinkService.class));
        Optional<ComplianceCase> created = service.resolveAlertToCase(70L, AlertDisposition.ESCALATED, "hit", null);

        assertNotNull(created.orElseThrow().getSuspicionFormedAt());
        assertEquals(formed, created.get().getSuspicionFormedAt());
    }

    private static void assertNullSafeOpen(ComplianceCase created) {
        assertEquals(CaseStatus.NEW, created.getStatus());
    }

    private static User user(Long id, Long pspId) {
        User user = new User();
        user.setId(id);
        user.setUsername("u" + id);
        Psp psp = new Psp();
        psp.setPspId(pspId);
        user.setPsp(psp);
        return user;
    }
}
