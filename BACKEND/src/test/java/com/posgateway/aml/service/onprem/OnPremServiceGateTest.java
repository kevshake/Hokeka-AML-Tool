package com.posgateway.aml.service.onprem;

import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import com.posgateway.aml.dto.onprem.OnPremLeaseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnPremServiceGateTest {

    private OnPremServiceGate gate;

    @BeforeEach
    void setUp() {
        gate = new OnPremServiceGate(new HokekaAuthProperties());
    }

    @Test
    void enabledDefaultsToStopped() {
        HokekaAuthProperties props = new HokekaAuthProperties();
        props.setEnabled(true);
        OnPremServiceGate onPrem = new OnPremServiceGate(props);
        assertFalse(onPrem.isOperationsAllowed());
    }

    @Test
    void disabledAllowsOperations() {
        assertTrue(gate.isOperationsAllowed());
    }

    @Test
    void runningLeaseAllowsUntilCheckDue() {
        Instant validUntil = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant nextCheck = Instant.now().plus(12, ChronoUnit.HOURS);
        gate.applyLease(new OnPremLeaseResponse(
                "inst", 1L, validUntil, nextCheck, 5, 1, "tok", "HokekaLease", Instant.now().getEpochSecond()));
        assertTrue(gate.isOperationsAllowed());
    }

    @Test
    void overdueCheckFailsClosed() {
        Instant validUntil = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant nextCheck = Instant.now().minus(1, ChronoUnit.MINUTES);
        gate.applyLease(new OnPremLeaseResponse(
                "inst", 1L, validUntil, nextCheck, 5, 1, "tok", "HokekaLease", Instant.now().getEpochSecond()));
        assertFalse(gate.isOperationsAllowed());
    }

    @Test
    void stopBlocksOperations() {
        gate.stop("upstream unreachable");
        assertFalse(gate.isOperationsAllowed());
    }
}
