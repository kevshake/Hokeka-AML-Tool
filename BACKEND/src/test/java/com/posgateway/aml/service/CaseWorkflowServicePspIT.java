package com.posgateway.aml.service;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for the orphan-case bug.
 *
 * <p>Previously, {@link CaseWorkflowService#createCase} did not stamp a pspId
 * onto newly created compliance cases. As a result, every case was orphaned
 * from its tenant — the PSP-isolated queries (e.g.
 * {@code findByPspId}) excluded them, and platform admins had to clean them
 * up manually. The fix copies {@code creator.getPsp().getPspId()} onto the
 * case at creation time. This test pins that behavior.
 */
@ExtendWith(MockitoExtension.class)
class CaseWorkflowServicePspIT {

    @Mock
    private ComplianceCaseRepository caseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CaseWorkflowService caseWorkflowService;

    private User creator;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setName("COMPLIANCE_OFFICER");

        Psp psp = Psp.builder().pspId(7L).build();

        creator = new User();
        creator.setId(42L);
        creator.setUsername("alice");
        creator.setRole(role);
        creator.setPsp(psp);
    }

    @Test
    void createCase_stampsCreatorPspIdOntoCase() {
        when(permissionService.hasPermission(any(Role.class), any(Permission.class))).thenReturn(true);
        when(caseRepository.save(any(ComplianceCase.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplianceCase result = caseWorkflowService.createCase(
                "CASE-TEST-001", "Suspicious wires", CasePriority.HIGH, creator);

        ArgumentCaptor<ComplianceCase> captor = ArgumentCaptor.forClass(ComplianceCase.class);
        verify(caseRepository).save(captor.capture());

        assertThat(captor.getValue().getPspId())
                .as("Newly created case must inherit the creator's PSP id (was the orphan-case bug).")
                .isEqualTo(7L);
        assertThat(result.getPspId()).isEqualTo(7L);
    }

    @Test
    void createCase_withCreatorWithoutPsp_yieldsNullPspId() {
        // Platform admin (psp == null) — must not throw, just stamp null.
        creator.setPsp(null);
        when(permissionService.hasPermission(any(Role.class), any(Permission.class))).thenReturn(true);
        when(caseRepository.save(any(ComplianceCase.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplianceCase result = caseWorkflowService.createCase(
                "CASE-PLAT-001", "Platform-level case", CasePriority.MEDIUM, creator);

        assertThat(result.getPspId()).isNull();
    }
}
