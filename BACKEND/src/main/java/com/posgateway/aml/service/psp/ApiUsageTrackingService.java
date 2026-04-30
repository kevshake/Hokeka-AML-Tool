package com.posgateway.aml.service.psp;



import com.posgateway.aml.dto.psp.ApiUsageEvent;
import com.posgateway.aml.entity.psp.ApiUsageLog;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.ApiUsageLogRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.PrometheusMetricsService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

// @RequiredArgsConstructor removed
@Service
public class ApiUsageTrackingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiUsageTrackingService.class);

    private final ApiUsageLogRepository apiUsageLogRepository;
    private final PspRepository pspRepository;
    private final UserRepository userRepository;
    private final BillingService billingService;
    private final PrometheusMetricsService metricsService;

    public ApiUsageTrackingService(ApiUsageLogRepository apiUsageLogRepository, PspRepository pspRepository, UserRepository userRepository, BillingService billingService, PrometheusMetricsService metricsService) {
        this.apiUsageLogRepository = apiUsageLogRepository;
        this.pspRepository = pspRepository;
        this.userRepository = userRepository;
        this.billingService = billingService;
        this.metricsService = metricsService;
    }


    @Async
    @Transactional
    public void logRequest(ApiUsageEvent event) {
        log.debug("Logging API usage for PSP: {}", event.getPspId());

        try {
            Psp psp = pspRepository.findById(event.getPspId())
                    .orElseThrow(() -> new IllegalArgumentException("PSP not found: " + event.getPspId()));

            com.posgateway.aml.entity.User user = null;
            if (event.getUserId() != null) {
                user = userRepository.findById(event.getUserId()).orElse(null);
            }

            // Calculate cost
            BigDecimal cost = billingService.calculateUsageCost(event.getPspId(), event.getServiceType(), 1);

            ApiUsageLog usageLog = ApiUsageLog.builder()
                    .psp(psp)
                    .user(user)
                    .endpoint(event.getEndpoint())
                    .httpMethod(event.getHttpMethod())
                    .requestTimestamp(event.getTimestamp())
                    .responseStatus(event.getResponseStatus())
                    .responseTimeMs(event.getResponseTimeMs())
                    .serviceType(event.getServiceType())
                    .billable(cost.compareTo(BigDecimal.ZERO) > 0)
                    .costAmount(cost)
                    .costCurrency("USD") // Should ideally come from billing rate
                    .requestId(event.getRequestId())
                    .merchantId(event.getMerchantId())
                    .ipAddress(event.getIpAddress())
                    .userAgent(event.getUserAgent())
                    .externalProvider(event.getExternalProvider())
                    .externalCost(event.getExternalCost())
                    .requestSizeBytes(event.getRequestSizeBytes())
                    .responseSizeBytes(event.getResponseSizeBytes())
                    .errorMessage(event.getErrorMessage())
                    .build();

            apiUsageLogRepository.save(usageLog);

            // Record revenue metrics for Prometheus
            if (cost.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    metricsService.recordRevenue(
                        cost.doubleValue(),
                        psp.getPspId(),
                        psp.getPspCode(),
                        event.getServiceType()
                    );
                } catch (Exception metricsEx) {
                    log.warn("Failed to record revenue metrics", metricsEx);
                    // Don't fail the main flow if metrics recording fails
                }
            }

        } catch (Exception e) {
            log.error("Failed to log API usage event", e);
            // Don't rethrow, strictly logging shouldn't fail the main flow if it's async
        }
    }
}
