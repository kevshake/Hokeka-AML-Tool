package com.posgateway.aml.service;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the fix for W19-5: the global (psp_id IS NULL) PSP_ADMIN template role RoleService
 * seeds on startup used to grant only view-level permissions -- no MANAGE_USERS, no MANAGE_RULES.
 * Every PSP registered outside the one-time V127 demo seed has no per-PSP PSP_ADMIN row, so
 * PspService.createPspUser's role lookup falls back to this global template, meaning a real PSP's
 * "administrator" could not manage their own PSP's users or rules.
 */
class RoleServiceDefaultRolesTest {

    @Test
    void seededPspAdminTemplateCanManageUsersAndRules() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        when(roleRepository.findByNameAndPspIsNull("PSP_ADMIN")).thenReturn(Optional.empty());
        // Other roles created by the same @PostConstruct aren't this test's concern; let them
        // "already exist" so initializeSystemRole skips saving them.
        when(roleRepository.findByNameAndPspIsNull("SUPER_ADMIN")).thenReturn(Optional.of(new Role()));
        when(roleRepository.findByNameAndPspIsNull("ADMIN")).thenReturn(Optional.of(new Role()));
        when(roleRepository.findByNameAndPspIsNull("COMPLIANCE_OFFICER")).thenReturn(Optional.of(new Role()));
        when(roleRepository.findByNameAndPspIsNull("INVESTIGATOR")).thenReturn(Optional.of(new Role()));
        when(roleRepository.findByNameAndPspIsNull("ANALYST")).thenReturn(Optional.of(new Role()));
        when(roleRepository.findByNameAndPspIsNull("VIEWER")).thenReturn(Optional.of(new Role()));
        when(roleRepository.save(org.mockito.ArgumentMatchers.any(Role.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RoleService service = new RoleService(roleRepository);
        service.initDefaultRoles();

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        Role pspAdmin = captor.getValue();

        assertTrue(pspAdmin.getPermissions().contains(Permission.MANAGE_USERS),
                "PSP_ADMIN template must be able to manage its own PSP's users");
        assertTrue(pspAdmin.getPermissions().contains(Permission.MANAGE_RULES),
                "PSP_ADMIN template must be able to manage its own PSP's rules");
    }
}
