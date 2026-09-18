package com.posgateway.aml.config.security;

import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import com.posgateway.aml.service.onprem.OnPremServiceGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnPremLeaseGateFilterTest {

    private HokekaAuthProperties properties;
    private OnPremServiceGate gate;
    private OnPremLeaseGateFilter filter;

    @BeforeEach
    void setUp() {
        properties = new HokekaAuthProperties();
        properties.setEnabled(true);
        gate = new OnPremServiceGate(properties);
        filter = new OnPremLeaseGateFilter(properties, gate);
    }

    @Test
    void blocksIngestWhenStopped() throws Exception {
        gate.stop("unreachable");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/transactions/ingest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("SERVICE_AUTHORIZATION_STOPPED"));
    }

    @Test
    void allowsHealthWhenStopped() throws Exception {
        gate.stop("unreachable");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void noopWhenAuthDisabled() throws Exception {
        properties.setEnabled(false);
        gate.stop("unreachable");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/transactions/ingest");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }
}
