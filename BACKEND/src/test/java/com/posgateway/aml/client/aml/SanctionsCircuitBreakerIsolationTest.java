package com.posgateway.aml.client.aml;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the fix for W14-5: sanctions screening (SanctionsScreenClient, SanctionsCountClient)
 * used to share the "amlMicroservice" circuit breaker with the unrelated AML risk-scoring path
 * (ScoringService). Resilience4j breaker instances are shared by name application-wide, so a burst
 * of sanctions-service 503s opened the shared breaker and degraded AML risk scoring too, even
 * though nothing was actually wrong with that path. Both sanctions clients must now share a
 * DEDICATED breaker name, distinct from "amlMicroservice". The count probe (SanctionsCountClient) is
 * deliberately not on any breaker.
 */
class SanctionsCircuitBreakerIsolationTest {

    private String circuitBreakerNameOn(Class<?> clazz, String methodName, Class<?>... paramTypes) throws Exception {
        Method m = clazz.getDeclaredMethod(methodName, paramTypes);
        CircuitBreaker cb = m.getAnnotation(CircuitBreaker.class);
        return cb.name();
    }

    @Test
    void sanctionsScreenClientNoLongerSharesTheAmlMicroserviceBreaker() throws Exception {
        String name = circuitBreakerNameOn(SanctionsScreenClient.class, "doScreen",
                SanctionsScreenClient.BackendSanctionsScreenRequest.class);
        assertNotEquals("amlMicroservice", name);
    }

    /**
     * The count is a health probe: it carries no breaker at all, so probe timeouts can never open
     * the breaker that protects real screening traffic, and the probe always reports live state.
     */
    @Test
    void sanctionsCountProbeIsNotOnAnyBreaker() {
        for (Method m : SanctionsCountClient.class.getDeclaredMethods()) {
            assertNull(m.getAnnotation(CircuitBreaker.class), "unexpected breaker on " + m.getName());
        }
    }

    @Test
    void screeningUsesTheDedicatedSanctionsBreaker() throws Exception {
        String screenBreaker = circuitBreakerNameOn(SanctionsScreenClient.class, "doScreen",
                SanctionsScreenClient.BackendSanctionsScreenRequest.class);
        assertEquals("sanctionsScreening", screenBreaker);
    }
}
