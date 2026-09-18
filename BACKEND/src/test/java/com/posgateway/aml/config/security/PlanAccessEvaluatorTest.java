package com.posgateway.aml.config.security;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.service.psp.EntitlementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The plan-feature gate: platform admins (no pspId) bypass, subscribed tenants are held to their
 * tier's features, and non-user principals are denied.
 */
@ExtendWith(MockitoExtension.class)
class PlanAccessEvaluatorTest {

    @Mock private EntitlementService entitlementService;
    @InjectMocks private PlanAccessEvaluator evaluator;

    private Authentication authFor(User user) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getPrincipal()).thenReturn(user);
        return auth;
    }

    @Test
    void adminWithNoPspBypassesGate() {
        User user = mock(User.class);
        when(user.getPsp()).thenReturn(null);
        assertTrue(evaluator.hasFeature(authFor(user), "custom_rules"));
    }

    @Test
    void subscribedTenantIsHeldToItsTierFeatures() {
        Psp psp = mock(Psp.class);
        when(psp.getPspId()).thenReturn(3L);
        User user = mock(User.class);
        when(user.getPsp()).thenReturn(psp);

        when(entitlementService.hasFeature(3L, "custom_rules")).thenReturn(true);
        when(entitlementService.hasFeature(3L, "white_label")).thenReturn(false);

        assertTrue(evaluator.hasFeature(authFor(user), "custom_rules"));
        assertFalse(evaluator.hasFeature(authFor(user), "white_label"));
    }

    @Test
    void nonUserPrincipalIsDenied() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getPrincipal()).thenReturn("anonymousUser");
        assertFalse(evaluator.hasFeature(auth, "custom_rules"));
    }

    @Test
    void nullAuthenticationIsDenied() {
        assertFalse(evaluator.hasFeature(null, "custom_rules"));
    }
}
