package com.posgateway.aml.config.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies W37-3: the M-Pesa callback endpoint gets a real, working IP/CIDR allowlist when
 * MPESA_CALLBACK_ALLOWED_IPS is configured, and stays open (matching pre-existing behaviour, not a
 * regression) when it is unconfigured.
 */
class MpesaCallbackIpAllowlistFilterTest {

    @Test
    void unconfiguredAllowlistLetsAnyIpThrough() throws Exception {
        MpesaCallbackIpAllowlistFilter filter = new MpesaCallbackIpAllowlistFilter("");

        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/api/v1/billing/payments/mpesa/callback");
        request.setRemoteAddr("203.0.113.99"); // arbitrary, unrelated IP
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus()); // untouched by the filter
    }

    @Test
    void configuredAllowlistRejectsAnIpOutsideIt() throws Exception {
        MpesaCallbackIpAllowlistFilter filter = new MpesaCallbackIpAllowlistFilter("196.201.214.0/24");

        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/api/v1/billing/payments/mpesa/callback");
        request.setRemoteAddr("8.8.8.8"); // outside the configured range
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(0)).doFilter(request, response);
        assertEquals(403, response.getStatus());
    }

    @Test
    void configuredAllowlistAcceptsAnIpInsideIt() throws Exception {
        MpesaCallbackIpAllowlistFilter filter = new MpesaCallbackIpAllowlistFilter("196.201.214.0/24");

        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/api/v1/billing/payments/mpesa/callback");
        request.setRemoteAddr("196.201.214.17"); // inside the configured /24
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void nonCallbackPathsAreNeverFiltered() {
        MpesaCallbackIpAllowlistFilter filter = new MpesaCallbackIpAllowlistFilter("196.201.214.0/24");

        MockHttpServletRequest unrelated = new MockHttpServletRequest("GET", "/api/v1/transactions");
        assertTrue(shouldNotFilter(filter, unrelated));

        MockHttpServletRequest callback = new MockHttpServletRequest("POST",
                "/api/v1/billing/payments/mpesa/callback");
        assertFalse(shouldNotFilter(filter, callback));
    }

    private boolean shouldNotFilter(MpesaCallbackIpAllowlistFilter filter, MockHttpServletRequest request) {
        try {
            java.lang.reflect.Method m = org.springframework.web.filter.OncePerRequestFilter.class
                    .getDeclaredMethod("shouldNotFilter", jakarta.servlet.http.HttpServletRequest.class);
            m.setAccessible(true);
            return (boolean) m.invoke(filter, request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
