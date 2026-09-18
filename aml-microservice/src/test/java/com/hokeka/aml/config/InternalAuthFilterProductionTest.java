package com.hokeka.aml.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalAuthFilterProductionTest {

    @Test
    void productionWithoutKeyReturns503() throws Exception {
        MockEnvironment env = new MockEnvironment().withProperty("spring.profiles.active", "production");
        InternalAuthFilter filter = new InternalAuthFilter("", env);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/cache/risk/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void devWithoutKeyAllowsInternalPath() throws Exception {
        MockEnvironment env = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        InternalAuthFilter filter = new InternalAuthFilter("", env);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/cache/risk/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
