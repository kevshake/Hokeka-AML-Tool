package com.posgateway.aml.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the fix for W19-4: UserSkillController's @PreAuthorize annotations referenced
 * hasAuthority('MANAGE_SKILLS')/hasAuthority('CERTIFY_SKILLS') on every endpoint, but neither
 * was ever registered in this enum -- the authority check could never match, so those endpoints
 * were reachable ONLY via the hardcoded role list (SUPER_ADMIN/ADMIN/COMPLIANCE_OFFICER)
 * alongside it, defeating the point of a fine-grained permission as an alternative grant path.
 */
class PermissionTest {

    @Test
    void skillPermissionsReferencedByUserSkillControllerAreRegistered() {
        assertDoesNotThrow(() -> Permission.valueOf("MANAGE_SKILLS"));
        assertDoesNotThrow(() -> Permission.valueOf("CERTIFY_SKILLS"));

        assertEquals(Permission.MANAGE_SKILLS, Permission.valueOf("MANAGE_SKILLS"));
        assertEquals(Permission.CERTIFY_SKILLS, Permission.valueOf("CERTIFY_SKILLS"));
    }
}
