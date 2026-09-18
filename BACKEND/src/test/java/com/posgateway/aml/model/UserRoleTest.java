package com.posgateway.aml.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every role name seeded in Flyway (roles.name) or initialized by RoleService must resolve via
 * {@link UserRole#valueOf(String)} so controllers using UserRole.valueOf(user.getRole().getName())
 * never throw on legitimate accounts.
 */
class UserRoleTest {

    private static final String[] SEEDED_ROLE_NAMES = {
            "SUPER_ADMIN", "PLATFORM_ADMIN", "ADMIN", "MLRO", "COMPLIANCE_OFFICER",
            "INVESTIGATOR", "ANALYST", "SCREENING_ANALYST", "CASE_MANAGER", "AUDITOR",
            "VIEWER", "PSP_ADMIN", "PSP_ANALYST", "PSP_USER", "USER", "APP_CONTROLLER",
            "BANK_OFFICER", "BANK_AUDITOR", "SENIOR_ANALYST"
    };

    @Test
    void everySeededRoleNameResolvesInEnum() {
        for (String roleName : SEEDED_ROLE_NAMES) {
            assertDoesNotThrow(() -> UserRole.valueOf(roleName), "Missing UserRole constant: " + roleName);
        }
    }

    @Test
    void registrationFallbackRolesResolve() {
        assertEquals(UserRole.PSP_USER, UserRole.valueOf("PSP_USER"));
        assertEquals(UserRole.USER, UserRole.valueOf("USER"));
        assertEquals(UserRole.PLATFORM_ADMIN, UserRole.valueOf("PLATFORM_ADMIN"));
        assertEquals(UserRole.APP_CONTROLLER, UserRole.valueOf("APP_CONTROLLER"));
    }

    @Test
    void enumCoversAllSeededNames() {
        for (String roleName : SEEDED_ROLE_NAMES) {
            assertDoesNotThrow(() -> UserRole.valueOf(roleName));
        }
        long distinct = Arrays.stream(SEEDED_ROLE_NAMES).distinct().count();
        assertEquals(SEEDED_ROLE_NAMES.length, distinct, "Test fixture contains duplicate role names");
    }
}
