package com.posgateway.aml.dto.psp;

import java.time.LocalDateTime;

public class ApiUsageEvent {
    private Long pspId;
    private Long userId;
    private String endpoint;
    private String httpMethod;
    private Integer responseStatus;
    private Integer responseTimeMs;
    private String serviceType;
    private String requestId;
    private Long merchantId;
    private String ipAddress;
    private String userAgent;
    private String externalProvider;
    private java.math.BigDecimal externalCost;
    private Integer requestSizeBytes;
    private Integer responseSizeBytes;
    private String errorMessage;
    private LocalDateTime timestamp = LocalDateTime.now();

    public ApiUsageEvent() {
    }

    public ApiUsageEvent(Long pspId, Long userId, String endpoint, String httpMethod, Integer responseStatus,
            Integer responseTimeMs, String serviceType, String requestId, Long merchantId, String ipAddress,
            String userAgent, String externalProvider, java.math.BigDecimal externalCost, Integer requestSizeBytes,
            Integer responseSizeBytes, String errorMessage, LocalDateTime timestamp) {
        this.pspId = pspId;
        this.userId = userId;
        this.endpoint = endpoint;
        this.httpMethod = httpMethod;
        this.responseStatus = responseStatus;
        this.responseTimeMs = responseTimeMs;
        this.serviceType = serviceType;
        this.requestId = requestId;
        this.merchantId = merchantId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.externalProvider = externalProvider;
        this.externalCost = externalCost;
        this.requestSizeBytes = requestSizeBytes;
        this.responseSizeBytes = responseSizeBytes;
        this.errorMessage = errorMessage;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Integer getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Integer responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getExternalProvider() {
        return externalProvider;
    }

    public void setExternalProvider(String externalProvider) {
        this.externalProvider = externalProvider;
    }

    public java.math.BigDecimal getExternalCost() {
        return externalCost;
    }

    public void setExternalCost(java.math.BigDecimal externalCost) {
        this.externalCost = externalCost;
    }

    public Integer getRequestSizeBytes() {
        return requestSizeBytes;
    }

    public void setRequestSizeBytes(Integer requestSizeBytes) {
        this.requestSizeBytes = requestSizeBytes;
    }

    public Integer getResponseSizeBytes() {
        return responseSizeBytes;
    }

    public void setResponseSizeBytes(Integer responseSizeBytes) {
        this.responseSizeBytes = responseSizeBytes;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public static ApiUsageEventBuilder builder() {
        return new ApiUsageEventBuilder();
    }

    public static class ApiUsageEventBuilder {
        private Long pspId;
        private Long userId;
        private String endpoint;
        private String httpMethod;
        private Integer responseStatus;
        private Integer responseTimeMs;
        private String serviceType;
        private String requestId;
        private Long merchantId;
        private String ipAddress;
        private String userAgent;
        private String externalProvider;
        private java.math.BigDecimal externalCost;
        private Integer requestSizeBytes;
        private Integer responseSizeBytes;
        private String errorMessage;
        private LocalDateTime timestamp = LocalDateTime.now();

        ApiUsageEventBuilder() {
        }

        public ApiUsageEventBuilder pspId(Long pspId) {
            this.pspId = pspId;
            return this;
        }

        public ApiUsageEventBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public ApiUsageEventBuilder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public ApiUsageEventBuilder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public ApiUsageEventBuilder responseStatus(Integer responseStatus) {
            this.responseStatus = responseStatus;
            return this;
        }

        public ApiUsageEventBuilder responseTimeMs(Integer responseTimeMs) {
            this.responseTimeMs = responseTimeMs;
            return this;
        }

        public ApiUsageEventBuilder serviceType(String serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        public ApiUsageEventBuilder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public ApiUsageEventBuilder merchantId(Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public ApiUsageEventBuilder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public ApiUsageEventBuilder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public ApiUsageEventBuilder externalProvider(String externalProvider) {
            this.externalProvider = externalProvider;
            return this;
        }

        public ApiUsageEventBuilder externalCost(java.math.BigDecimal externalCost) {
            this.externalCost = externalCost;
            return this;
        }

        public ApiUsageEventBuilder requestSizeBytes(Integer requestSizeBytes) {
            this.requestSizeBytes = requestSizeBytes;
            return this;
        }

        public ApiUsageEventBuilder responseSizeBytes(Integer responseSizeBytes) {
            this.responseSizeBytes = responseSizeBytes;
            return this;
        }

        public ApiUsageEventBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public ApiUsageEventBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ApiUsageEvent build() {
            return new ApiUsageEvent(pspId, userId, endpoint, httpMethod, responseStatus, responseTimeMs, serviceType,
                    requestId, merchantId, ipAddress, userAgent, externalProvider, externalCost, requestSizeBytes,
                    responseSizeBytes, errorMessage, timestamp);
        }

        public String toString() {
            return "ApiUsageEvent.ApiUsageEventBuilder(pspId=" + this.pspId + ", userId=" + this.userId + ", endpoint="
                    + this.endpoint + ", httpMethod=" + this.httpMethod + ", responseStatus=" + this.responseStatus
                    + ", responseTimeMs=" + this.responseTimeMs + ", serviceType=" + this.serviceType + ", requestId="
                    + this.requestId + ", merchantId=" + this.merchantId + ", ipAddress=" + this.ipAddress
                    + ", userAgent=" + this.userAgent + ", externalProvider=" + this.externalProvider
                    + ", externalCost=" + this.externalCost + ", requestSizeBytes=" + this.requestSizeBytes
                    + ", responseSizeBytes=" + this.responseSizeBytes + ", errorMessage=" + this.errorMessage
                    + ", timestamp=" + this.timestamp + ")";
        }
    }
}
