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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * The merged R3 + R4 exemption list: every machine-to-machine Edge path (including
     * {@code /edge/config} and the {@code /edge/tunnel} upgrade) is exempt under the servlet
     * context path, while the fleet admin API under {@code /edge/nodes} still enforces activation.
     */
    @Test
    void edgeMachinePathsAreExemptButTheFleetAdminApiIsNot() throws Exception {
        for (String path : new String[]{"/edge/enroll", "/edge/bundle", "/edge/metrics",
                "/edge/assessments", "/edge/config", "/edge/tunnel"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1" + path);
            request.setContextPath("/api/v1");
            assertTrue(filter.shouldNotFilter(request), path + " must be exempt");
        }
        // R10b: the sanctions list manifest and chunks, with and without the servlet context path.
        String chunk = "/edge/sanctions/list/" + "a".repeat(64) + "/chunks/3";
        for (String path : new String[]{"/edge/sanctions/list", chunk}) {
            MockHttpServletRequest withContext = new MockHttpServletRequest("GET", "/api/v1" + path);
            withContext.setContextPath("/api/v1");
            assertTrue(filter.shouldNotFilter(withContext), path + " must be exempt under /api/v1");
            MockHttpServletRequest bare = new MockHttpServletRequest("GET", "/api/v1" + path);
            assertTrue(filter.shouldNotFilter(bare), "/api/v1" + path + " must be exempt without a context path");
            assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", path)), path + " must be exempt");
        }
        MockHttpServletRequest notAChunk = new MockHttpServletRequest("GET", "/edge/sanctions/other");
        assertFalse(filter.shouldNotFilter(notAChunk), "only the list paths are exempt");
        // R10c: the feature-bag endpoint is gone, so it is no longer a machine path either.
        MockHttpServletRequest removed = new MockHttpServletRequest("POST", "/api/v1/edge/decision");
        removed.setContextPath("/api/v1");
        assertFalse(filter.shouldNotFilter(removed), "/edge/decision was removed in R10c");
        MockHttpServletRequest admin = new MockHttpServletRequest("GET", "/api/v1/edge/nodes");
        admin.setContextPath("/api/v1");
        assertFalse(filter.shouldNotFilter(admin), "/edge/nodes is a user API");
        assertEquals(403, run("/edge/nodes", user("PENDING")).getStatus());
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
