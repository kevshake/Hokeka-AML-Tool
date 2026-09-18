package com.posgateway.aml.dto.psp;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detailed PSP usage response with service-level breakdown.
 */
public class PspUsageResponse {

    private final Long pspId;
    private final String pspName;
    private final String period;
    private final long totalRequests;
    private final long billableRequests;
    private final BigDecimal totalCost;
    private final List<ServiceBreakdown> serviceBreakdown;
    private final String subscriptionStatus;
    private final String pricingTier;

    public PspUsageResponse(Long pspId, String pspName, String period,
                             long totalRequests, long billableRequests,
                             BigDecimal totalCost, List<ServiceBreakdown> serviceBreakdown,
                             String subscriptionStatus, String pricingTier) {
        this.pspId = pspId;
        this.pspName = pspName;
        this.period = period;
        this.totalRequests = totalRequests;
        this.billableRequests = billableRequests;
        this.totalCost = totalCost;
        this.serviceBreakdown = serviceBreakdown;
        this.subscriptionStatus = subscriptionStatus;
        this.pricingTier = pricingTier;
    }

    public Long getPspId() { return pspId; }
    public String getPspName() { return pspName; }
    public String getPeriod() { return period; }
    public long getTotalRequests() { return totalRequests; }
    public long getBillableRequests() { return billableRequests; }
    public BigDecimal getTotalCost() { return totalCost; }
    public List<ServiceBreakdown> getServiceBreakdown() { return serviceBreakdown; }
    public String getSubscriptionStatus() { return subscriptionStatus; }
    public String getPricingTier() { return pricingTier; }

    public static class ServiceBreakdown {
        private final String serviceType;
        private final long requestCount;
        private final BigDecimal cost;

        public ServiceBreakdown(String serviceType, long requestCount, BigDecimal cost) {
            this.serviceType = serviceType;
            this.requestCount = requestCount;
            this.cost = cost;
        }

        public String getServiceType() { return serviceType; }
        public long getRequestCount() { return requestCount; }
        public BigDecimal getCost() { return cost; }
    }
}