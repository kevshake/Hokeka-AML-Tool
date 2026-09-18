package com.posgateway.aml.controller;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.service.case_management.CasePermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceCaseControllerTest {

    @Mock private ComplianceCaseRepository caseRepository;
    @Mock private CasePermissionService casePermissionService;

    private ComplianceCaseController controller;

    @BeforeEach
    void setUp() {
        controller = new ComplianceCaseController(caseRepository, casePermissionService);

        Role role = new Role();
        role.setName("PSP_ADMIN");
        Psp psp = new Psp();
        psp.setPspId(7L);
        User user = new User();
        user.setUsername("psp-admin");
        user.setRole(role);
        user.setPsp(psp);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "n/a", user.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pspStatisticsUseAllOpenStatusesAndExactTenantTotal() {
        when(caseRepository.countByPspIdAndStatus(7L, CaseStatus.NEW)).thenReturn(2L);
        when(caseRepository.countByPspIdAndStatus(7L, CaseStatus.ASSIGNED)).thenReturn(3L);
        when(caseRepository.countByPspIdAndStatus(7L, CaseStatus.IN_PROGRESS)).thenReturn(4L);
        when(caseRepository.countByPspId(7L)).thenReturn(20L);

        ResponseEntity<ComplianceCaseController.CaseStats> response = controller.getStats();

        ComplianceCaseController.CaseStats stats = response.getBody();
        assertNotNull(stats);
        assertEquals(9L, stats.getOpenCases());
        assertEquals(4L, stats.getInProgressCases());
        assertEquals(20L, stats.getTotalCases());
    }
}
