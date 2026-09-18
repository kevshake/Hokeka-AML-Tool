package com.posgateway.aml.dto.psp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Consolidated PSP billing summary for the SaaS admin portal.
 */
public class PspBillingSummaryResponse {

    private final Long pspId;
    private final String pspCode;
    private final String legalName;
    private final String tradingName;
    private final String contactEmail;
    private final String subscriptionStatus;
    private final String pricingTier;
    private final long totalRequests;
    private final long billableRequests;
    private final BigDecimal currentMonthCost;
    private final String latestInvoiceStatus;
    private final BigDecimal latestInvoiceAmount;
    private final long overdueInvoiceCount;
    private final LocalDateTime createdAt;

    public PspBillingSummaryResponse(Long pspId, String pspCode, String legalName,
                                      String tradingName, String contactEmail,
                                      String subscriptionStatus, String pricingTier,
                                      long totalRequests, long billableRequests,
                                      BigDecimal currentMonthCost,
                                      String latestInvoiceStatus,
                                      BigDecimal latestInvoiceAmount,
                                      long overdueInvoiceCount,
                                      LocalDateTime createdAt) {
        this.pspId = pspId;
        this.pspCode = pspCode;
        this.legalName = legalName;
        this.tradingName = tradingName;
        this.contactEmail = contactEmail;
        this.subscriptionStatus = subscriptionStatus;
        this.pricingTier = pricingTier;
        this.totalRequests = totalRequests;
        this.billableRequests = billableRequests;
        this.currentMonthCost = currentMonthCost;
        this.latestInvoiceStatus = latestInvoiceStatus;
        this.latestInvoiceAmount = latestInvoiceAmount;
        this.overdueInvoiceCount = overdueInvoiceCount;
        this.createdAt = createdAt;
    }

    public Long getPspId() { return pspId; }
    public String getPspCode() { return pspCode; }
    public String getLegalName() { return legalName; }
    public String getTradingName() { return tradingName; }
    public String getContactEmail() { return contactEmail; }
    public String getSubscriptionStatus() { return subscriptionStatus; }
    public String getPricingTier() { return pricingTier; }
    public long getTotalRequests() { return totalRequests; }
    public long getBillableRequests() { return billableRequests; }
    public BigDecimal getCurrentMonthCost() { return currentMonthCost; }
    public String getLatestInvoiceStatus() { return latestInvoiceStatus; }
    public BigDecimal getLatestInvoiceAmount() { return latestInvoiceAmount; }
    public long getOverdueInvoiceCount() { return overdueInvoiceCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}