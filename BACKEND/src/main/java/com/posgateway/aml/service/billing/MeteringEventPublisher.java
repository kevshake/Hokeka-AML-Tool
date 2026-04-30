package com.posgateway.aml.service.billing;

import com.posgateway.aml.dto.psp.ApiUsageEvent;
import com.posgateway.aml.service.psp.ApiUsageTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Metering Event Publisher
 * Fire-and-forget metering that NEVER blocks transaction processing.
 * This is the integration point for recording billable events.
 */
@Service
public class MeteringEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MeteringEventPublisher.class);

    private final ApiUsageTrackingService usageTracker;

    public MeteringEventPublisher(ApiUsageTrackingService usageTracker) {
        this.usageTracker = usageTracker;
    }

    /**
     * Record an AML screening event - fire-and-forget
     */
    @Async("meteringExecutor")
    public void recordAmlScreening(Long pspId, String transactionId, Long userId) {
        recordEvent(MeteringEvent.builder()
                .pspId(pspId)
                .serviceType("AML_SCREENING")
                .requestId(transactionId)
                .userId(userId)
                .build());
    }

    /**
     * Record a KYC verification event - fire-and-forget
     */
    @Async("meteringExecutor")
    public void recordKycVerification(Long pspId, String applicantId, Long userId) {
        recordEvent(MeteringEvent.builder()
                .pspId(pspId)
                .serviceType("KYC_VERIFICATION")
                .requestId(applicantId)
                .userId(userId)
                .build());
    }

    /**
     * Record a sanctions screening event - fire-and-forget
     */
    @Async("meteringExecutor")
    public void recordSanctionsScreening(Long pspId, String entityId, Long userId) {
        recordEvent(MeteringEvent.builder()
                .pspId(pspId)
                .serviceType("SANCTIONS_SCREENING")
                .requestId(entityId)
                .userId(userId)
                .build());
    }

    /**
     * Record a transaction monitoring event - fire-and-forget
     */
    @Async("meteringExecutor")
    public void recordTransactionMonitoring(Long pspId, String transactionId, Long userId) {
        recordEvent(MeteringEvent.builder()
                .pspId(pspId)
                .serviceType("TRANSACTION_MONITORING")
                .requestId(transactionId)
                .userId(userId)
                .build());
    }

    /**
     * Generic event recording - fire-and-forget
     */
    @Async("meteringExecutor")
    public void recordEvent(MeteringEvent event) {
        try {
            log.debug("Recording metering event: {} for PSP {}", event.getServiceType(), event.getPspId());
            usageTracker.logRequest(event.toApiUsageEvent());
        } catch (Exception e) {
            // Never throw - metering failure should not impact transactions
            log.error("Failed to record metering event for PSP {}: {}", event.getPspId(), e.getMessage());
        }
    }

    /**
     * Metering Event DTO
     */
    public static class MeteringEvent {
        private Long pspId;
        private Long userId;
        private String serviceType;
        private String requestId;
        private String endpoint;
        private Long merchantId;
        private Integer responseStatus;
        private Long responseTimeMs;
        private LocalDateTime timestamp;

        public static Builder builder() {
            return new Builder();
        }

        public ApiUsageEvent toApiUsageEvent() {
            ApiUsageEvent event = new ApiUsageEvent();
            event.setPspId(pspId);
            event.setUserId(userId);
            event.setServiceType(serviceType);
            event.setRequestId(requestId);
            event.setEndpoint(endpoint != null ? endpoint : "/api/v1/" + serviceType.toLowerCase());
            event.setHttpMethod("POST");
            event.setMerchantId(merchantId);
            event.setResponseStatus(responseStatus != null ? responseStatus : 200);
            event.setResponseTimeMs(responseTimeMs != null ? responseTimeMs.intValue() : 0);
            event.setTimestamp(timestamp != null ? timestamp : LocalDateTime.now());
            return event;
        }

        // Getters
        public Long getPspId() {
            return pspId;
        }

        public Long getUserId() {
            return userId;
        }

        public String getServiceType() {
            return serviceType;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public Long getMerchantId() {
            return merchantId;
        }

        public Integer getResponseStatus() {
            return responseStatus;
        }

        public Long getResponseTimeMs() {
            return responseTimeMs;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public static class Builder {
            private final MeteringEvent event = new MeteringEvent();

            public Builder pspId(Long pspId) {
                event.pspId = pspId;
                return this;
            }

            public Builder userId(Long userId) {
                event.userId = userId;
                return this;
            }

            public Builder serviceType(String serviceType) {
                event.serviceType = serviceType;
                return this;
            }

            public Builder requestId(String requestId) {
                event.requestId = requestId;
                return this;
            }

            public Builder endpoint(String endpoint) {
                event.endpoint = endpoint;
                return this;
            }

            public Builder merchantId(Long merchantId) {
                event.merchantId = merchantId;
                return this;
            }

            public Builder responseStatus(Integer status) {
                event.responseStatus = status;
                return this;
            }

            public Builder responseTimeMs(Long ms) {
                event.responseTimeMs = ms;
                return this;
            }

            public Builder timestamp(LocalDateTime ts) {
                event.timestamp = ts;
                return this;
            }

            public MeteringEvent build() {
                return event;
            }
        }
    }
}
