package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Every PSP-bound role is confined to its own PSP's cases; platform admins see all. */
class CasePermissionServiceTenancyTest {

    private final ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
    private final PspIsolationService isolation = mock(PspIsolationService.class);
    private final CasePermissionService service = new CasePermissionService(cases, isolation);

    private static User user(String role, Long pspId) {
        Role r = new Role();
        r.setName(role);
        Psp psp = null;
        if (pspId != null) {
            psp = new Psp();
            psp.setPspId(pspId);
        }
        return User.builder().id(1L).username(role).email(role + "@x").passwordHash("").role(r).psp(psp).build();
    }

    private void caseOfPsp(long caseId, long pspId) {
        ComplianceCase kase = new ComplianceCase();
        kase.setPspId(pspId);
        when(cases.findById(caseId)).thenReturn(Optional.of(kase));
    }

    @Test
    void pspBoundUsersOnlySeeTheirOwnPsp() {
        caseOfPsp(1L, 5L);
        caseOfPsp(2L, 6L);
        for (String role : new String[]{"PSP_USER", "COMPLIANCE_OFFICER", "PSP_ADMIN", "INVESTIGATOR"}) {
            User u = user(role, 5L);
            assertThat(service.canView(1L, u)).as(role + " own PSP").isTrue();
            assertThat(service.canView(2L, u)).as(role + " other PSP").isFalse();
            assertThat(service.canAct(2L, u, "ESCALATE")).as(role + " act on other PSP").isFalse();
        }
    }

    @Test
    void platformAdminsSeeEveryPspAndMissingCasesAreHidden() {
        caseOfPsp(2L, 6L);
        when(cases.findById(3L)).thenReturn(Optional.empty());
        User admin = user("SUPER_ADMIN", 0L);
        when(isolation.isPlatformAdministrator(any(User.class))).thenReturn(true);

        assertThat(service.canView(2L, admin)).isTrue();
        assertThat(service.canView(3L, admin)).isFalse();
    }
}
