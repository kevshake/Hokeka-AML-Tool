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
        // W19-2 added three more roles to the same @PostConstruct (SCREENING_ANALYST,
        // PSP_ANALYST, APP_CONTROLLER) -- also not this test's concern.
        when(roleRepository.findByNameAndPspIsNull("SCREENING_ANALYST")).thenReturn(Optional.of(new Role()));
        when(roleRepository.findByNameAndPspIsNull("PSP_ANALYST")).thenReturn(Optional.of(new Role()));
        when(roleRepository.findByNameAndPspIsNull("APP_CONTROLLER")).thenReturn(Optional.of(new Role()));
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

    /**
     * Verifies W19-2: SCREENING_ANALYST, PSP_ANALYST, and APP_CONTROLLER are documented, real
     * role names referenced across 17+ @PreAuthorize annotations throughout the codebase, but
     * no Role row with any of the three names was ever seeded -- every one of those branches was
     * permanently dead.
     */
    @Test
    void previouslyUnprovisionedRolesAreNowSeeded() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        // Everything except the three new roles "already exists" -- not this test's concern.
        for (String existing : new String[]{"SUPER_ADMIN", "ADMIN", "PSP_ADMIN", "COMPLIANCE_OFFICER",
                "INVESTIGATOR", "ANALYST", "VIEWER"}) {
            when(roleRepository.findByNameAndPspIsNull(existing)).thenReturn(Optional.of(new Role()));
        }
        when(roleRepository.findByNameAndPspIsNull("SCREENING_ANALYST")).thenReturn(Optional.empty());
        when(roleRepository.findByNameAndPspIsNull("PSP_ANALYST")).thenReturn(Optional.empty());
        when(roleRepository.findByNameAndPspIsNull("APP_CONTROLLER")).thenReturn(Optional.empty());
        when(roleRepository.save(org.mockito.ArgumentMatchers.any(Role.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RoleService service = new RoleService(roleRepository);
        service.initDefaultRoles();

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, org.mockito.Mockito.times(3)).save(captor.capture());

        var savedNames = captor.getAllValues().stream().map(Role::getName).toList();
        assertTrue(savedNames.contains("SCREENING_ANALYST"));
        assertTrue(savedNames.contains("PSP_ANALYST"));
        assertTrue(savedNames.contains("APP_CONTROLLER"));

        Role screeningAnalyst = captor.getAllValues().stream()
                .filter(r -> "SCREENING_ANALYST".equals(r.getName())).findFirst().orElseThrow();
        assertTrue(screeningAnalyst.getPermissions().contains(Permission.MANAGE_WATCHLISTS));

        Role pspAnalyst = captor.getAllValues().stream()
                .filter(r -> "PSP_ANALYST".equals(r.getName())).findFirst().orElseThrow();
        assertTrue(pspAnalyst.getPermissions().contains(Permission.VIEW_CASES));
    }
}
