package com.posgateway.aml.client.aml;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies the fix for W14-5: sanctions screening (SanctionsScreenClient, SanctionsCountClient)
 * used to share the "amlMicroservice" circuit breaker with the unrelated AML risk-scoring path
 * (ScoringService). Resilience4j breaker instances are shared by name application-wide, so a burst
 * of sanctions-service 503s opened the shared breaker and degraded AML risk scoring too, even
 * though nothing was actually wrong with that path. Both sanctions clients must now share a
 * DEDICATED breaker name, distinct from "amlMicroservice".
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

    @Test
    void sanctionsCountClientNoLongerSharesTheAmlMicroserviceBreaker() throws Exception {
        String name = circuitBreakerNameOn(SanctionsCountClient.class, "doGetCount");
        assertNotEquals("amlMicroservice", name);
    }

    @Test
    void bothSanctionsClientsShareTheSameDedicatedBreaker() throws Exception {
        String screenBreaker = circuitBreakerNameOn(SanctionsScreenClient.class, "doScreen",
                SanctionsScreenClient.BackendSanctionsScreenRequest.class);
        String countBreaker = circuitBreakerNameOn(SanctionsCountClient.class, "doGetCount");
        // Same downstream dependency, same failure domain -- correctly correlated with each other,
        // just decoupled from the unrelated AML-scoring breaker.
        assertEquals(screenBreaker, countBreaker);
        assertEquals("sanctionsScreening", screenBreaker);
    }
}
