package com.posgateway.aml.dto.billing;

import java.math.BigDecimal;

/**
 * Platform-level revenue KPI snapshot for the admin revenue dashboard.
 */
public class RevenueSummaryResponse {

    private BigDecimal currentMonthRevenuePaid;
    private BigDecimal currentMonthRevenueExpected;
    private BigDecimal overdueAmount;
    private long activeSubscriptions;
    private long paidInvoicesThisMonth;
    private long overdueInvoicesCount;
    private String currency;

    public RevenueSummaryResponse() {
    }

    public RevenueSummaryResponse(BigDecimal currentMonthRevenuePaid, BigDecimal currentMonthRevenueExpected,
            BigDecimal overdueAmount, long activeSubscriptions, long paidInvoicesThisMonth,
            long overdueInvoicesCount, String currency) {
        this.currentMonthRevenuePaid = currentMonthRevenuePaid;
        this.currentMonthRevenueExpected = currentMonthRevenueExpected;
        this.overdueAmount = overdueAmount;
        this.activeSubscriptions = activeSubscriptions;
        this.paidInvoicesThisMonth = paidInvoicesThisMonth;
        this.overdueInvoicesCount = overdueInvoicesCount;
        this.currency = currency;
    }

    public BigDecimal getCurrentMonthRevenuePaid() { return currentMonthRevenuePaid; }
    public void setCurrentMonthRevenuePaid(BigDecimal currentMonthRevenuePaid) { this.currentMonthRevenuePaid = currentMonthRevenuePaid; }

    public BigDecimal getCurrentMonthRevenueExpected() { return currentMonthRevenueExpected; }
    public void setCurrentMonthRevenueExpected(BigDecimal currentMonthRevenueExpected) { this.currentMonthRevenueExpected = currentMonthRevenueExpected; }

    public BigDecimal getOverdueAmount() { return overdueAmount; }
    public void setOverdueAmount(BigDecimal overdueAmount) { this.overdueAmount = overdueAmount; }

    public long getActiveSubscriptions() { return activeSubscriptions; }
    public void setActiveSubscriptions(long activeSubscriptions) { this.activeSubscriptions = activeSubscriptions; }

    public long getPaidInvoicesThisMonth() { return paidInvoicesThisMonth; }
    public void setPaidInvoicesThisMonth(long paidInvoicesThisMonth) { this.paidInvoicesThisMonth = paidInvoicesThisMonth; }

    public long getOverdueInvoicesCount() { return overdueInvoicesCount; }
    public void setOverdueInvoicesCount(long overdueInvoicesCount) { this.overdueInvoicesCount = overdueInvoicesCount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
