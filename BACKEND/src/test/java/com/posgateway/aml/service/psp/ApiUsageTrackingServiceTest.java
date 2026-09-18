package com.posgateway.aml.service.psp;

import com.posgateway.aml.dto.psp.ApiUsageEvent;
import com.posgateway.aml.entity.psp.ApiUsageLog;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.ApiUsageLogRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.PrometheusMetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A metered request is only billable when it actually succeeded: a PSP must not be charged for its
 * own 4xx rejections or the platform's 5xx errors.
 */
@ExtendWith(MockitoExtension.class)
class ApiUsageTrackingServiceTest {

    @Mock private ApiUsageLogRepository apiUsageLogRepository;
    @Mock private PspRepository pspRepository;
    @Mock private UserRepository userRepository;
    @Mock private BillingService billingService;
    @Mock private PrometheusMetricsService metricsService;

    @InjectMocks private ApiUsageTrackingService service;

    private ApiUsageEvent event(int status) {
        return ApiUsageEvent.builder()
                .pspId(1L)
                .endpoint("/api/v1/aml/check")
                .httpMethod("POST")
                .responseStatus(status)
                .responseTimeMs(12)
                .serviceType("AML_SCREENING")
                .timestamp(LocalDateTime.now())
                .build();
    }

    private ApiUsageLog logAfter(int status) {
        Psp psp = new Psp();
        psp.setPspCode("ACME");
        ReflectionTestUtils.setField(psp, "pspId", 1L);
        when(pspRepository.findById(1L)).thenReturn(Optional.of(psp));
        when(billingService.calculateUsageCost(eq(1L), eq("AML_SCREENING"), anyInt()))
                .thenReturn(new BigDecimal("0.05"));
        lenient().when(billingService.getEffectiveCurrency(eq(1L), eq("AML_SCREENING"))).thenReturn("USD");

        service.logRequest(event(status));

        ArgumentCaptor<ApiUsageLog> captor = ArgumentCaptor.forClass(ApiUsageLog.class);
        verify(apiUsageLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void successfulRequestIsBillable() {
        assertTrue(logAfter(200).getBillable());
    }

    @Test
    void clientErrorIsNotBilled() {
        assertFalse(logAfter(400).getBillable(), "4xx must not be billed");
    }

    @Test
    void serverErrorIsNotBilled() {
        assertFalse(logAfter(500).getBillable(), "5xx must not be billed");
    }
}
