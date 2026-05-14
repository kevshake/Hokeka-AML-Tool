package com.posgateway.aml.dto.billing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Usage summary for a PSP for a given billing period.
 */
public class UsageSummaryResponse {

    private Long pspId;
    private String period;
    private long totalRequests;
    private long billableRequests;
    private BigDecimal totalCostUsd;
    private List<ServiceBreakdown> breakdown;

    public UsageSummaryResponse() {
    }

    public UsageSummaryResponse(Long pspId, String period, long totalRequests, long billableRequests,
            BigDecimal totalCostUsd, List<ServiceBreakdown> breakdown) {
        this.pspId = pspId;
        this.period = period;
        this.totalRequests = totalRequests;
        this.billableRequests = billableRequests;
        this.totalCostUsd = totalCostUsd;
        this.breakdown = breakdown;
    }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }

    public long getBillableRequests() { return billableRequests; }
    public void setBillableRequests(long billableRequests) { this.billableRequests = billableRequests; }

    public BigDecimal getTotalCostUsd() { return totalCostUsd; }
    public void setTotalCostUsd(BigDecimal totalCostUsd) { this.totalCostUsd = totalCostUsd; }

    public List<ServiceBreakdown> getBreakdown() { return breakdown; }
    public void setBreakdown(List<ServiceBreakdown> breakdown) { this.breakdown = breakdown; }

    // -------------------------------------------------------------------------
    // Inner DTO for per-service breakdown
    // -------------------------------------------------------------------------

    public static class ServiceBreakdown {
        private String serviceType;
        private long count;
        private BigDecimal costUsd;

        public ServiceBreakdown() {
        }

        public ServiceBreakdown(String serviceType, long count, BigDecimal costUsd) {
            this.serviceType = serviceType;
            this.count = count;
            this.costUsd = costUsd;
        }

        public String getServiceType() { return serviceType; }
        public void setServiceType(String serviceType) { this.serviceType = serviceType; }

        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }

        public BigDecimal getCostUsd() { return costUsd; }
        public void setCostUsd(BigDecimal costUsd) { this.costUsd = costUsd; }
    }
}
