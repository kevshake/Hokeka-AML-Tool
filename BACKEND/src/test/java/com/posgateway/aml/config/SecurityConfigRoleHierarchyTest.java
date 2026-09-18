package com.posgateway.aml.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors {@link SecurityConfig#roleHierarchy()} so PLATFORM_ADMIN satisfies
 * {@code hasAnyRole('ADMIN', ...)} on dashboard and monitoring controllers.
 */
class SecurityConfigRoleHierarchyTest {

    private RoleHierarchyImpl hierarchy;

    @BeforeEach
    void setUp() {
        hierarchy = new RoleHierarchyImpl();
        hierarchy.setHierarchy(
                "ROLE_SUPER_ADMIN > ROLE_ADMIN\n" +
                "ROLE_PLATFORM_ADMIN > ROLE_ADMIN\n" +
                "ROLE_ADMIN > ROLE_COMPLIANCE_OFFICER\n" +
                "ROLE_ADMIN > ROLE_INVESTIGATOR\n" +
                "ROLE_ADMIN > ROLE_ANALYST\n" +
                "ROLE_ADMIN > ROLE_VIEWER\n" +
                "ROLE_ADMIN > ROLE_PSP_ADMIN\n" +
                "ROLE_PSP_ADMIN > ROLE_PSP_USER\n" +
                "ROLE_COMPLIANCE_OFFICER > ROLE_INVESTIGATOR\n" +
                "ROLE_INVESTIGATOR > ROLE_ANALYST\n" +
                "ROLE_ANALYST > ROLE_VIEWER"
        );
    }

    @Test
    void platformAdminInheritsAdminForMethodSecurity() {
        var reachable = hierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        assertTrue(reachable.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test
    void platformAdminInheritsComplianceOfficerForDashboardAccess() {
        var reachable = hierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        assertTrue(reachable.stream().anyMatch(a -> "ROLE_COMPLIANCE_OFFICER".equals(a.getAuthority())));
    }

    @Test
    void pspAdminDoesNotInheritAdmin() {
        var reachable = hierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_PSP_ADMIN")));
        assertFalse(reachable.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }
}
