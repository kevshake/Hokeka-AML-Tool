package com.posgateway.aml.config.security;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Closes the "approval is decorative" hole: {@code Psp.status} defaulted to PENDING, PSP
 * self-registration is public, and {@code Psp.isActive()} had no caller in any request path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PspActivationFilterTest {

    @Mock private PspIsolationService pspIsolationService;

    private PspActivationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PspActivationFilter(pspIsolationService);
    }

    @Test
    void aUserOfAPendingPspIsRefusedWithAnAwaitingApprovalCode() throws Exception {
        MockHttpServletResponse response = run("/cases", user("PENDING"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("PSP_AWAITING_APPROVAL"));
        assertTrue(response.getContentAsString().contains("awaiting approval"));
    }

    @Test
    void aUserOfASuspendedPspIsRefusedWithAnAccessRevokedCode() throws Exception {
        MockHttpServletResponse response = run("/cases", user("SUSPENDED"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("PSP_ACCESS_REVOKED"));
        assertTrue(response.getContentAsString().contains("revoked"));
    }

    @Test
    void aUserOfATerminatedPspIsRefused() throws Exception {
        MockHttpServletResponse response = run("/transactions", user("TERMINATED"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("PSP_ACCESS_REVOKED"));
    }

    @Test
    void aUserOfAnActivePspPassesThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("/cases", user("ACTIVE"), chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "the request must reach the rest of the chain");
    }

    @Test
    void platformAdministratorsAreUnaffected() throws Exception {
        User admin = user("PENDING");
        when(pspIsolationService.isPlatformAdministrator(any(User.class))).thenReturn(true);

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("/cases", admin, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void anonymousTrafficIsLeftToSpringSecurity() throws Exception {
        when(pspIsolationService.getCurrentUser()).thenReturn(null);

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("/cases", null, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void publicPathsStayReachableForAPendingPsp() throws Exception {
        for (String path : new String[]{"/auth/login", "/psps/register", "/pricing/plans",
                "/actuator/health", "/merchants/onboard", "/edge/bundle"}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = run(path, user("PENDING"), chain);
            assertEquals(200, response.getStatus(), path + " must stay public");
            assertNotNull(chain.getRequest(), path + " must reach the chain");
        }
    }

    @Test
    void exemptionsAreMatchedAfterStrippingTheServletContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContextPath("/api/v1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(pspIsolationService.getCurrentUser()).thenReturn(user("PENDING"));

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void aLookupFailureNeverBecomesAnOpenDoorNorABrokenRequest() throws Exception {
        when(pspIsolationService.getCurrentUser()).thenThrow(new SecurityException("boom"));

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("/cases", null, chain);

        // Spring Security still guards the endpoint; this filter only adds the PSP-status check.
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void aRefusalDoesNotReachTheRestOfTheChain() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        run("/cases", user("PENDING"), chain);
        assertNull(chain.getRequest());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private MockHttpServletResponse run(String path, User user) throws Exception {
        return run(path, user, new MockFilterChain());
    }

    private MockHttpServletResponse run(String path, User user, MockFilterChain chain)
            throws Exception {
        if (user != null) {
            when(pspIsolationService.getCurrentUser()).thenReturn(user);
        }
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static User user(String pspStatus) {
        Psp psp = new Psp();
        psp.setPspId(7L);
        psp.setStatus(pspStatus);
        return User.builder().username("psp-user").psp(psp).build();
    }
}
