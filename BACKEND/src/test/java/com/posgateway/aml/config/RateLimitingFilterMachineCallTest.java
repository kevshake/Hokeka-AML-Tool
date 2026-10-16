package com.posgateway.aml.config;

import com.posgateway.aml.config.security.PspApiKeyAuthenticationFilter;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.service.psp.QuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression (sim run 2026-10-16): API-key ingest was counted against the shared per-tenant
 * 120/min limit, so integrations got 429 far below their documented per-key limit (1200/min).
 */
class RateLimitingFilterMachineCallTest {

    private final QuotaService quota = mock(QuotaService.class);
    private final RateLimitingFilter filter = new RateLimitingFilter(quota);

    @BeforeEach
    void tenantUser() {
        Psp psp = new Psp();
        psp.setPspId(5L);
        User user = new User();
        user.setPsp(psp);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        when(quota.isWithinRateLimit(anyLong())).thenReturn(false);
        when(quota.isWithinMonthlyQuota(anyLong())).thenReturn(true);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void apiKeyCallIsNotCountedAgainstTheTenantPerMinuteLimit() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/transactions/ingest");
        req.setAttribute(PspApiKeyAuthenticationFilter.API_KEY_ID_ATTRIBUTE, 9L);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).as("request reached the endpoint").isNotNull();
        verify(quota, never()).isWithinRateLimit(anyLong());
        verify(quota).isWithinMonthlyQuota(5L); // plan quota still applies to machine calls
    }

    @Test
    void consoleCallOverTheTenantLimitStillGets429() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/alerts");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest()).isNull();
    }
}
