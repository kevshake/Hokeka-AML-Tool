package com.posgateway.aml.controller;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.PermissionService;
import com.posgateway.aml.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (no Spring context) for UserController's PSP tenant-isolation checks.
 *
 * Verifies the fix for the cross-tenant IDOR vulnerability where a PSP-scoped admin with
 * MANAGE_USERS permission could edit, delete, or toggle any user in the system by guessing a
 * numeric user id, because updateUser/deleteUser (unlike the sibling PATCH .../toggle endpoint,
 * and unlike createUser's own targetPsp check) never verified the target user belonged to the
 * caller's own PSP.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTenantIsolationTest {

    @Mock
    private UserService userService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private PspRepository pspRepository;
    @Mock
    private UserRepository userRepository;

    private UserController controller;

    private Psp psp1;
    private Psp psp2;
    private User pspAdmin1;
    private User targetUser2; // belongs to PSP2, a different tenant than pspAdmin1

    @BeforeEach
    void setUp() {
        controller = new UserController(userService, permissionService, pspRepository, userRepository);

        psp1 = new Psp();
        psp1.setPspId(1L);
        psp1.setPspCode("PSP001");

        psp2 = new Psp();
        psp2.setPspId(2L);
        psp2.setPspCode("PSP002");

        pspAdmin1 = new User();
        pspAdmin1.setId(100L);
        pspAdmin1.setUsername("admin_psp1");
        pspAdmin1.setPsp(psp1);

        targetUser2 = new User();
        targetUser2.setId(200L);
        targetUser2.setUsername("user_psp2");
        targetUser2.setPsp(psp2);

        lenient().when(permissionService.hasPermission(any(), any())).thenReturn(true);
    }

    @Test
    void updateUserAcrossTenantsThrowsSecurityExceptionBeforeMutating() {
        when(userService.getUserById(200L)).thenReturn(targetUser2);

        UserController.UpdateUserRequest req = new UserController.UpdateUserRequest();
        req.setFirstName("Hacked");
        req.setLastName("User");
        req.setEmail("hacked@test.com");

        assertThrows(SecurityException.class, () -> controller.updateUser(200L, req, pspAdmin1));

        // The IDOR fix's whole point: the mutation must never happen, not just that an
        // exception surfaces afterward.
        verify(userService, org.mockito.Mockito.never()).updateUser(anyLong(), any(), any());
    }

    @Test
    void deleteUserAcrossTenantsThrowsSecurityExceptionBeforeMutating() {
        when(userService.getUserById(200L)).thenReturn(targetUser2);

        assertThrows(SecurityException.class, () -> controller.deleteUser(200L, pspAdmin1));

        verify(userService, org.mockito.Mockito.never()).deleteUser(anyLong());
    }

    @Test
    void toggleUserStatusAcrossTenantsThrowsSecurityExceptionBeforeMutating() {
        when(userService.getUserById(200L)).thenReturn(targetUser2);

        assertThrows(SecurityException.class, () -> controller.toggleUserStatus(200L, "enable", pspAdmin1));

        verify(userService, org.mockito.Mockito.never()).toggleUserStatus(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void updateUserSameTenantSucceeds() {
        User targetUser1 = new User();
        targetUser1.setId(101L);
        targetUser1.setUsername("user_psp1");
        targetUser1.setPsp(psp1); // same PSP as pspAdmin1

        when(userService.getUserById(101L)).thenReturn(targetUser1);
        when(userService.updateUser(org.mockito.ArgumentMatchers.eq(101L), any(), any()))
                .thenReturn(targetUser1);

        UserController.UpdateUserRequest req = new UserController.UpdateUserRequest();
        req.setFirstName("Updated");

        assertDoesNotThrow(() -> controller.updateUser(101L, req, pspAdmin1));
        verify(userService).updateUser(org.mockito.ArgumentMatchers.eq(101L), any(), any());
    }

    @Test
    void superAdminWithNullPspCanUpdateAnyTenantsUser() {
        User superAdmin = new User();
        superAdmin.setId(1L);
        superAdmin.setUsername("super_admin");
        superAdmin.setPsp(null); // platform admin: no PSP restriction

        when(userService.updateUser(org.mockito.ArgumentMatchers.eq(200L), any(), any()))
                .thenReturn(targetUser2);

        UserController.UpdateUserRequest req = new UserController.UpdateUserRequest();
        req.setFirstName("Updated");

        assertDoesNotThrow(() -> controller.updateUser(200L, req, superAdmin));
        // A platform admin must never trigger the ownership lookup at all — confirms the
        // requireSamePsp short-circuit for null-PSP callers, not just that it happens to pass.
        verify(userService, org.mockito.Mockito.never()).getUserById(anyLong());
    }
}
