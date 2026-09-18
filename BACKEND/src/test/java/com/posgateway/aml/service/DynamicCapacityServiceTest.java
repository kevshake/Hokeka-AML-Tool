package com.posgateway.aml.service;

import com.posgateway.aml.config.security.ProductionRateLimitFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicCapacityServiceTest {
    @Test
    void lowTrafficReducesLimitWithoutCrossingConfiguredFloor() {
        ProductionRateLimitFilter filter = new ProductionRateLimitFilter();
        filter.updateGeneralRequestsPerMinute(100);
        DynamicCapacityService service = new DynamicCapacityService(new SimpleMeterRegistry(), filter);
        service.setBoundsForTest(95, 1000);

        int adjusted = service.adjustCapacity();

        assertEquals(95, adjusted);
        assertEquals(95, filter.getGeneralRequestsPerMinute());
    }
}
