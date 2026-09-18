package com.posgateway.aml.service.onprem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnPremLeaseTokenServiceTest {

    private OnPremLeaseTokenService service;

    @BeforeEach
    void setUp() {
        HokekaAuthProperties props = new HokekaAuthProperties();
        props.setLeaseSigningSecret("unit-test-signing-secret-32chars!!");
        service = new OnPremLeaseTokenService(props, new ObjectMapper());
    }

    @Test
    void roundTripsClaims() {
        Instant validUntil = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant nextCheck = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String token = service.issueToken("inst-1", 42L, validUntil, nextCheck, 7, "jti-abc");

        OnPremLeaseTokenService.LeaseClaims claims = service.verify(token);
        assertEquals("inst-1", claims.instanceId());
        assertEquals(42L, claims.pspId());
        assertEquals(validUntil, claims.validUntil());
        assertEquals(nextCheck, claims.nextCheckAt());
        assertEquals(7, claims.approvedDays());
        assertEquals("jti-abc", claims.jti());
    }

    @Test
    void rejectsTamperedToken() {
        Instant validUntil = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant nextCheck = Instant.now().plus(12, ChronoUnit.HOURS);
        String token = service.issueToken("inst-1", 1L, validUntil, nextCheck, 1, "jti");
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertThrows(IllegalArgumentException.class, () -> service.verify(tampered));
    }

    @Test
    void requiresSigningSecret() {
        HokekaAuthProperties empty = new HokekaAuthProperties();
        OnPremLeaseTokenService bare = new OnPremLeaseTokenService(empty, new ObjectMapper());
        assertThrows(IllegalStateException.class,
                () -> bare.issueToken("x", 1L, Instant.now(), Instant.now(), 1, "j"));
    }

    @Test
    void stableMinuteOfDayIsBoundedAndStable() {
        int a = OnPremCheckInScheduler.stableMinuteOfDay("psp-node-alpha");
        int b = OnPremCheckInScheduler.stableMinuteOfDay("psp-node-alpha");
        assertEquals(a, b);
        assertTrue(a >= 0 && a < 24 * 60);
    }
}
