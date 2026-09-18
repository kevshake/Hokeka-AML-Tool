package com.posgateway.aml.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the fix for W20-8: AuthenticationController.register()'s default-role fallback chain
 * tries "VIEWER", then "PSP_USER", then "USER" (in that order) when no explicit role is provided.
 * PSP_USER and USER were both missing from the UserRole enum, so any account actually registered
 * under either fallback crashed with an uncaught IllegalArgumentException at every one of the ~20
 * call sites across the codebase that do UserRole.valueOf(user.getRole().getName()) -- case
 * management, PSP CBK-filing controllers, case permissions/escalation.
 */
class UserRoleTest {

    @Test
    void everyRegistrationFallbackRoleNameResolvesWithoutThrowing() {
        assertDoesNotThrow(() -> UserRole.valueOf("VIEWER"));
        assertDoesNotThrow(() -> UserRole.valueOf("PSP_USER"));
        assertDoesNotThrow(() -> UserRole.valueOf("USER"));

        assertEquals(UserRole.PSP_USER, UserRole.valueOf("PSP_USER"));
        assertEquals(UserRole.USER, UserRole.valueOf("USER"));
    }
}
