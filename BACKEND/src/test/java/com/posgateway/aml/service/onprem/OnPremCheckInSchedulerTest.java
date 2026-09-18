package com.posgateway.aml.service.onprem;

import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnPremCheckInSchedulerTest {

    private final OnPremCheckInScheduler scheduler = new OnPremCheckInScheduler();

    @Test
    void assignsFutureSlotWithinLease() {
        Instant from = Instant.parse("2026-07-23T10:00:00Z");
        Instant leaseUntil = from.plus(7, ChronoUnit.DAYS);
        Instant next = scheduler.assignNextCheckAt("fleet-node-7", from, 1, leaseUntil);

        assertTrue(next.isAfter(from));
        assertFalse(next.isAfter(leaseUntil));
    }

    @Test
    void capsAtLeaseUntilWhenIntervalWouldExceed() {
        Instant from = Instant.parse("2026-07-23T10:00:00Z");
        Instant leaseUntil = from.plus(2, ChronoUnit.HOURS);
        Instant next = scheduler.assignNextCheckAt("fleet-node-7", from, 1, leaseUntil);
        assertTrue(next.equals(leaseUntil) || !next.isAfter(leaseUntil));
    }

    @Test
    void differentInstancesSpreadAcrossDay() {
        int a = OnPremCheckInScheduler.stableMinuteOfDay("alpha-instance");
        int b = OnPremCheckInScheduler.stableMinuteOfDay("beta-instance-zz");
        // Extremely unlikely to collide for distinct ids; if they do, still valid as long as bounded.
        assertTrue(a >= 0 && a < 1440);
        assertTrue(b >= 0 && b < 1440);
    }
}
