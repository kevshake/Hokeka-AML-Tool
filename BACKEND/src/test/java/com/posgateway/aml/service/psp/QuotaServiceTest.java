package com.posgateway.aml.service.psp;

import com.posgateway.aml.repository.ApiUsageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Rate limiting and the plan monthly quota must both actually bite, keyed on the numeric pspId.
 */
@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock private EntitlementService entitlementService;
    @Mock private ApiUsageLogRepository apiUsageLogRepository;
    @InjectMocks private QuotaService service;

    @BeforeEach
    void setRate() {
        // @Value is not injected without a Spring context — set a small window for the test.
        ReflectionTestUtils.setField(service, "requestsPerMinute", 3);
    }

    @Test
    void rateLimiterBlocksAfterTheConfiguredBurst() {
        assertTrue(service.isWithinRateLimit(1L));
        assertTrue(service.isWithinRateLimit(1L));
        assertTrue(service.isWithinRateLimit(1L));
        assertFalse(service.isWithinRateLimit(1L), "4th request in the window is over the limit");
        // A different tenant has its own counter.
        assertTrue(service.isWithinRateLimit(2L));
    }

    @Test
    void nullTenantIsNeverRateLimited() {
        for (int i = 0; i < 10; i++) {
            assertTrue(service.isWithinRateLimit(null));
        }
    }

    @Test
    void monthlyQuotaEnforcedFromPlanLimit() {
        when(entitlementService.monthlyCheckLimit(eq(5L))).thenReturn(100);
        when(apiUsageLogRepository.countBillableRequests(eq(5L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(100L);
        assertFalse(service.isWithinMonthlyQuota(5L), "at the cap ⇒ blocked");
    }

    @Test
    void underMonthlyQuotaIsAllowed() {
        when(entitlementService.monthlyCheckLimit(eq(6L))).thenReturn(100);
        when(apiUsageLogRepository.countBillableRequests(eq(6L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(42L);
        assertTrue(service.isWithinMonthlyQuota(6L));
    }

    @Test
    void noPlanCapMeansUnlimited() {
        when(entitlementService.monthlyCheckLimit(eq(8L))).thenReturn(null);
        assertTrue(service.isWithinMonthlyQuota(8L));
    }
}
