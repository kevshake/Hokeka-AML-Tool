package com.posgateway.aml.service.residency;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Role gating of PSP data settings: PSP admins make, a different PSP reviewer checks, platform reads. */
class PspDataSettingsAccessTest {

    @Test
    void pspAdminOfThePspMayProposeAndOthersMayNot() {
        assertDoesNotThrow(() -> access(user("PSP_ADMIN", 12L), false).requireMaker(12L));
        assertThrows(SecurityException.class, () -> access(user("PSP_ADMIN", 13L), false).requireMaker(12L));
        assertThrows(SecurityException.class, () -> access(user("PSP_ANALYST", 12L), false).requireMaker(12L));
        assertThrows(SecurityException.class, () -> access(user("MLRO", 12L), false).requireMaker(12L));
        assertThrows(SecurityException.class, () -> access(user("SUPER_ADMIN", 0L), true).requireMaker(12L),
                "support access and retention are the PSP's own decision");
    }

    @Test
    void checkersAreTenantReviewers() {
        assertDoesNotThrow(() -> access(user("MLRO", 12L), false).requireChecker(12L));
        assertDoesNotThrow(() -> access(user("COMPLIANCE_OFFICER", 12L), false).requireChecker(12L));
        assertDoesNotThrow(() -> access(user("PSP_ADMIN", 12L), false).requireChecker(12L));
        assertThrows(SecurityException.class, () -> access(user("PSP_USER", 12L), false).requireChecker(12L));
        assertThrows(SecurityException.class, () -> access(user("MLRO", 13L), false).requireChecker(12L));
        assertThrows(SecurityException.class, () -> access(user("PLATFORM_ADMIN", 0L), true).requireChecker(12L));
    }

    @Test
    void readsAreTenantScopedAndPlatformOperatorsMayRead() {
        assertDoesNotThrow(() -> access(user("PSP_USER", 12L), false).requireRead(12L));
        assertThrows(SecurityException.class, () -> access(user("PSP_USER", 13L), false).requireRead(12L));
        assertDoesNotThrow(() -> access(user("PLATFORM_ADMIN", 0L), true).requireRead(12L));
        assertThrows(SecurityException.class, () -> new PspDataSettingsAccess(mock(PspIsolationService.class)).requireRead(12L),
                "unauthenticated");
    }

    @Test
    void edgeConfigEditorsArePspAdminsOrPlatformOperators() {
        assertDoesNotThrow(() -> access(user("PSP_ADMIN", 12L), false).requireConfigEditor(12L));
        assertDoesNotThrow(() -> access(user("SUPER_ADMIN", 0L), true).requireConfigEditor(12L));
        assertThrows(SecurityException.class, () -> access(user("COMPLIANCE_OFFICER", 12L), false).requireConfigEditor(12L));
    }

    private static PspDataSettingsAccess access(User user, boolean platform) {
        PspIsolationService isolation = mock(PspIsolationService.class);
        when(isolation.getCurrentUser()).thenReturn(user);
        when(isolation.isPlatformAdministrator(user)).thenReturn(platform);
        return new PspDataSettingsAccess(isolation);
    }

    private static User user(String role, Long pspId) {
        User user = new User();
        user.setId(pspId * 100 + role.length());
        user.setUsername(role.toLowerCase() + "@" + pspId);
        Role r = new Role();
        r.setName(role);
        user.setRole(r);
        Psp psp = new Psp();
        psp.setPspId(pspId);
        user.setPsp(psp);
        return user;
    }
}
