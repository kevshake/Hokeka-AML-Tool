package com.posgateway.aml.entity.psp;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API Usage Log Entity
 * Tracks all API requests for billing and analytics
 */
@Entity
@Table(name = "api_usage_logs")
public class ApiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "psp_id", nullable = false)
    private Psp psp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private com.posgateway.aml.entity.User user;

    // Request Details
    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "request_timestamp")
    private LocalDateTime requestTimestamp = LocalDateTime.now();

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    // Usage Categorization
    @Column(name = "service_type", nullable = false, length = 100)
    private String serviceType;

    @Column(name = "billable")
    private Boolean billable = true;

    @Column(name = "cost_amount", precision = 10, scale = 4)
    private BigDecimal costAmount;

    @Column(name = "cost_currency", length = 3)
    private String costCurrency = "USD";

    // Request Metadata
    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    // External Costs
    @Column(name = "external_provider", length = 100)
    private String externalProvider;

    @Column(name = "external_cost", precision = 10, scale = 4)
    private BigDecimal externalCost;

    // Additional Data
    @Column(name = "request_size_bytes")
    private Integer requestSizeBytes;

    @Column(name = "response_size_bytes")
    private Integer responseSizeBytes;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    public ApiUsageLog() {
    }

    public ApiUsageLog(Long logId, Psp psp, com.posgateway.aml.entity.User user, String endpoint, String httpMethod,
            LocalDateTime requestTimestamp, Integer responseStatus, Integer responseTimeMs, String serviceType,
            Boolean billable, BigDecimal costAmount, String costCurrency, String requestId, Long merchantId,
            String ipAddress, String userAgent, String externalProvider, BigDecimal externalCost,
            Integer requestSizeBytes, Integer responseSizeBytes, String errorMessage) {
        this.logId = logId;
        this.psp = psp;
        this.user = user;
        this.endpoint = endpoint;
        this.httpMethod = httpMethod;
        this.requestTimestamp = requestTimestamp != null ? requestTimestamp : LocalDateTime.now();
        this.responseStatus = responseStatus;
        this.responseTimeMs = responseTimeMs;
        this.serviceType = serviceType;
        this.billable = billable != null ? billable : true;
        this.costAmount = costAmount;
        this.costCurrency = costCurrency != null ? costCurrency : "USD";
        this.requestId = requestId;
        this.merchantId = merchantId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.externalProvider = externalProvider;
        this.externalCost = externalCost;
        this.requestSizeBytes = requestSizeBytes;
        this.responseSizeBytes = responseSizeBytes;
        this.errorMessage = errorMessage;
    }

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public Psp getPsp() {
        return psp;
    }

    public void setPsp(Psp psp) {
        this.psp = psp;
    }

    public com.posgateway.aml.entity.User getUser() {
        return user;
    }

    public void setUser(com.posgateway.aml.entity.User user) {
        this.user = user;
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

    public LocalDateTime getRequestTimestamp() {
        return requestTimestamp;
    }

    public void setRequestTimestamp(LocalDateTime requestTimestamp) {
        this.requestTimestamp = requestTimestamp;
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

    public Boolean getBillable() {
        return billable;
    }

    public void setBillable(Boolean billable) {
        this.billable = billable;
    }

    public BigDecimal getCostAmount() {
        return costAmount;
    }

    public void setCostAmount(BigDecimal costAmount) {
        this.costAmount = costAmount;
    }

    public String getCostCurrency() {
        return costCurrency;
    }

    public void setCostCurrency(String costCurrency) {
        this.costCurrency = costCurrency;
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

    public BigDecimal getExternalCost() {
        return externalCost;
    }

    public void setExternalCost(BigDecimal externalCost) {
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

    public static ApiUsageLogBuilder builder() {
        return new ApiUsageLogBuilder();
    }

    public static class ApiUsageLogBuilder {
        private Long logId;
        private Psp psp;
        private com.posgateway.aml.entity.User user;
        private String endpoint;
        private String httpMethod;
        private LocalDateTime requestTimestamp = LocalDateTime.now();
        private Integer responseStatus;
        private Integer responseTimeMs;
        private String serviceType;
        private Boolean billable = true;
        private BigDecimal costAmount;
        private String costCurrency = "USD";
        private String requestId;
        private Long merchantId;
        private String ipAddress;
        private String userAgent;
        private String externalProvider;
        private BigDecimal externalCost;
        private Integer requestSizeBytes;
        private Integer responseSizeBytes;
        private String errorMessage;

        ApiUsageLogBuilder() {
        }

        public ApiUsageLogBuilder logId(Long logId) {
            this.logId = logId;
            return this;
        }

        public ApiUsageLogBuilder psp(Psp psp) {
            this.psp = psp;
            return this;
        }

        public ApiUsageLogBuilder user(com.posgateway.aml.entity.User user) {
            this.user = user;
            return this;
        }

        public ApiUsageLogBuilder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public ApiUsageLogBuilder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public ApiUsageLogBuilder requestTimestamp(LocalDateTime requestTimestamp) {
            this.requestTimestamp = requestTimestamp;
            return this;
        }

        public ApiUsageLogBuilder responseStatus(Integer responseStatus) {
            this.responseStatus = responseStatus;
            return this;
        }

        public ApiUsageLogBuilder responseTimeMs(Integer responseTimeMs) {
            this.responseTimeMs = responseTimeMs;
            return this;
        }

        public ApiUsageLogBuilder serviceType(String serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        public ApiUsageLogBuilder billable(Boolean billable) {
            this.billable = billable;
            return this;
        }

        public ApiUsageLogBuilder costAmount(BigDecimal costAmount) {
            this.costAmount = costAmount;
            return this;
        }

        public ApiUsageLogBuilder costCurrency(String costCurrency) {
            this.costCurrency = costCurrency;
            return this;
        }

        public ApiUsageLogBuilder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public ApiUsageLogBuilder merchantId(Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public ApiUsageLogBuilder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public ApiUsageLogBuilder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public ApiUsageLogBuilder externalProvider(String externalProvider) {
            this.externalProvider = externalProvider;
            return this;
        }

        public ApiUsageLogBuilder externalCost(BigDecimal externalCost) {
            this.externalCost = externalCost;
            return this;
        }

        public ApiUsageLogBuilder requestSizeBytes(Integer requestSizeBytes) {
            this.requestSizeBytes = requestSizeBytes;
            return this;
        }

        public ApiUsageLogBuilder responseSizeBytes(Integer responseSizeBytes) {
            this.responseSizeBytes = responseSizeBytes;
            return this;
        }

        public ApiUsageLogBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public ApiUsageLog build() {
            return new ApiUsageLog(logId, psp, user, endpoint, httpMethod, requestTimestamp, responseStatus,
                    responseTimeMs, serviceType, billable, costAmount, costCurrency, requestId, merchantId, ipAddress,
                    userAgent, externalProvider, externalCost, requestSizeBytes, responseSizeBytes, errorMessage);
        }

        public String toString() {
            return "ApiUsageLog.ApiUsageLogBuilder(logId=" + this.logId + ", psp=" + this.psp + ", user=" + this.user
                    + ", endpoint=" + this.endpoint + ", httpMethod=" + this.httpMethod + ", requestTimestamp="
                    + this.requestTimestamp + ", responseStatus=" + this.responseStatus + ", responseTimeMs="
                    + this.responseTimeMs + ", serviceType=" + this.serviceType + ", billable=" + this.billable
                    + ", costAmount=" + this.costAmount + ", costCurrency=" + this.costCurrency + ", requestId="
                    + this.requestId + ", merchantId=" + this.merchantId + ", ipAddress=" + this.ipAddress
                    + ", userAgent=" + this.userAgent + ", externalProvider=" + this.externalProvider
                    + ", externalCost=" + this.externalCost + ", requestSizeBytes=" + this.requestSizeBytes
                    + ", responseSizeBytes=" + this.responseSizeBytes + ", errorMessage=" + this.errorMessage + ")";
        }
    }
}
