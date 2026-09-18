package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.CaseQueueRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseAssignmentServiceIsolationTest {

    @Test
    void autoAssignmentIgnoresUsersFromOtherPsps() {
        ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
        CaseQueueRepository queues = mock(CaseQueueRepository.class);
        UserRepository users = mock(UserRepository.class);
        CaseAssignmentService service = new CaseAssignmentService(cases, queues, users);

        ComplianceCase complianceCase = new ComplianceCase();
        complianceCase.setId(100L);
        complianceCase.setPspId(42L);

        User otherPspUser = user(8L, 99L);
        when(users.findByRole_NameAndEnabled(UserRole.INVESTIGATOR.name(), true))
                .thenReturn(List.of(otherPspUser));

        User assigned = service.assignCaseByWorkload(complianceCase, UserRole.INVESTIGATOR);

        assertThat(assigned).isNull();
        verify(cases, never()).countByAssignedTo_IdAndStatusIn(eq(otherPspUser.getId()), anyList());
    }

    private User user(Long id, Long pspId) {
        Psp psp = new Psp();
        psp.setPspId(pspId);
        User user = new User();
        user.setId(id);
        user.setPsp(psp);
        return user;
    }
}
